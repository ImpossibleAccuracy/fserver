package com.fserver.core.storage.internal

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.fserver.core.files.SourceLocation
import com.fserver.core.storage.RegisteredSourcesRepository
import com.fserver.core.storage.database.FServerStorageDatabase
import com.fserver.core.store.sync.SourcesStore
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SourceTombstone
import com.fserver.core.sync.model.SyncMode
import com.fserver.core.util.TimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import com.fserver.core.storage.database.Attribute as DBAttribute
import com.fserver.core.storage.database.Source as DBSource

/**
 * Sources over two tables: the `source` row for what every source has, `attribute` for the fields
 * whichever variant it runs under carries. [SourceRecords] owns the translation both ways.
 *
 * Tombstones reuse that same attribute table under a different owner, so a deleted source's location
 * survives the row it came from.
 *
 * [index] and [remoteIndex] are held so [delete] can drop both sides' file records with the source:
 * unregistering must leave no orphan bookkeeping behind. It still touches no bytes on disk.
 */
internal class SourcesStoreImpl(
    private val database: FServerStorageDatabase,
    private val index: FileIndexStoreImpl,
    private val remoteIndex: RemoteIndexStoreImpl,
    private val timeProvider: TimeProvider,
) : SourcesStore, RegisteredSourcesRepository {
    private val dao = database.sourceQueries
    private val attributeDao = database.attributeQueries
    private val tombstoneDao = database.sourceTombstoneQueries

    // ---------------- SourcesStore: what the engine calls ----------------

    override suspend fun all(): List<SourceEntry> = read(dao.selectAll().executeAsList())

    override suspend fun findById(id: String): SourceEntry? =
        dao.selectById(id).executeAsOneOrNull()?.let { read(listOf(it)) }?.firstOrNull()

    /**
     * The discriminators narrow in SQL; the variants' own fields are compared in Kotlin, which is
     * what the attribute rows cost. Cheap because the WHERE has already cut the set to a handful.
     */
    override suspend fun findByModeAndLocation(
        mode: SyncMode,
        location: SourceLocation,
    ): SourceEntry? {
        if (location !is SourceLocation.Persistable) return null

        val candidates = dao.selectByLocationAndMode(
            location = SourceRecords.discriminatorOf(location),
            mode = SourceRecords.discriminatorOf(mode),
        ).executeAsList()

        return read(candidates).find { it.syncMode == mode && it.location == location }
    }

    /**
     * Row first, then attributes, and the old attributes dropped in between: `INSERT OR REPLACE`
     * leaves whatever the previous variant wrote, and a narrower one would inherit fields it does
     * not own.
     */
    override suspend fun upsert(source: SourceEntry) {
        database.transaction {
            dao.upsert(
                id = source.id,
                deviceId = source.deviceId,
                role = source.role.name,
                label = source.label,
                originPath = source.originPath,
                createdAtEpochMs = source.createdAt.toEpochMilliseconds(),
                lastSyncedAtEpochMs = source.lastSyncedAt?.toEpochMilliseconds(),
                location = SourceRecords.discriminatorOf(source.location),
                mode = SourceRecords.discriminatorOf(source.syncMode),
                status = SourceRecords.discriminatorOf(source.status),
            )

            attributeDao.deleteByOwnerId(
                owner = SourceRecords.OwnerSource,
                ownerId = source.id,
            )

            for (attribute in SourceRecords.attributesOf(source)) {
                attributeDao.insert(
                    owner = SourceRecords.OwnerSource,
                    ownerId = source.id,
                    type = attribute.type,
                    fieldName = attribute.field,
                    fieldValue = attribute.value,
                )
            }
        }
    }

    /** The status' own fields move with it: Disabled carries a reason that Active does not. */
    override suspend fun updateStatus(id: String, status: SourceEntry.Status) {
        database.transaction {
            dao.updateStatus(status = SourceRecords.discriminatorOf(status), id = id)

            attributeDao.deleteByType(
                owner = SourceRecords.OwnerSource,
                ownerId = id,
                type = SourceRecords.Status,
            )

            for (attribute in SourceRecords.statusAttributesOf(status)) {
                attributeDao.insert(
                    owner = SourceRecords.OwnerSource,
                    ownerId = id,
                    type = attribute.type,
                    fieldName = attribute.field,
                    fieldValue = attribute.value,
                )
            }
        }
    }

    override suspend fun delete(id: String) {
        val removed = findById(id)

        database.transaction {
            // The peer it synced with is read off the record on the way out: once the source is
            // gone, nothing else here remembers who to tell.
            if (removed != null) {
                tombstoneDao.upsert(
                    sourceId = id,
                    deviceId = removed.deviceId,
                    removedAtEpochMs = timeProvider.now().toEpochMilliseconds(),
                    location = SourceRecords.discriminatorOf(removed.location),
                )

                attributeDao.deleteByOwnerId(
                    owner = SourceRecords.OwnerTombstone,
                    ownerId = id,
                )

                for (attribute in SourceRecords.locationAttributesOf(removed.location)) {
                    attributeDao.insert(
                        owner = SourceRecords.OwnerTombstone,
                        ownerId = id,
                        type = attribute.type,
                        fieldName = attribute.field,
                        fieldValue = attribute.value,
                    )
                }
            }

            // The trigger on `source` covers this too; done here as well so the rows go even if a
            // migration rebuilt the table and dropped its triggers with it.
            attributeDao.deleteByOwnerId(
                owner = SourceRecords.OwnerSource,
                ownerId = id,
            )
            dao.delete(id)
        }

        index.clearProcessed(id)
        remoteIndex.clear(id)
    }

    override suspend fun findTombstone(id: String): SourceTombstone? {
        val row = tombstoneDao.selectById(id).executeAsOneOrNull() ?: return null

        val attributes = SourceRecords.Reader(
            attributeDao.selectByOwner(owner = SourceRecords.OwnerTombstone, ownerId = id)
                .executeAsList()
                .associate { (it.type to it.fieldName) to it.fieldValue }
        )

        val location = SourceRecords.locationOf(id, row.location, attributes) ?: return null

        return SourceRecords.tombstoneOf(
            sourceId = row.sourceId,
            deviceId = row.deviceId,
            removedAtEpochMs = row.removedAtEpochMs,
            location = location,
        )
    }

    // ---------------- RegisteredSourcesRepository: what a screen calls ----------------

    /**
     * Two queries combined rather than one join: attribute rows are few, and a join would have to be
     * regrouped anyway. Both tables invalidate the flow, so an attribute change re-emits.
     */
    override val sources: Flow<List<SourceEntry>> = combine(
        dao.selectAll().asFlow().mapToList(Dispatchers.IO),
        attributeDao.selectAllOf(SourceRecords.OwnerSource).asFlow().mapToList(Dispatchers.IO),
    ) { rows, attributes -> rows.assemble(attributes) }

    override fun observeById(id: String): Flow<SourceEntry?> =
        sources.map { current -> current.find { it.id == id } }

    override fun observeProcessedCount(id: String): Flow<Int> = index.observeProcessedCount(id)

    override suspend fun rename(id: String, label: String) {
        dao.updateLabel(label = label, id = id)
    }

    // ---------------- assembly ----------------

    /** Reads [rows] back, fetching only the attributes they actually need. */
    private fun read(rows: List<DBSource>): List<SourceEntry> {
        if (rows.isEmpty()) return emptyList()

        val sources = rows.mapTo(mutableSetOf()) { it.id }
        val attributes = attributeDao
            .selectByOwnerIds(owner = SourceRecords.OwnerSource, ownerId = sources)
            .executeAsList()

        return rows.assemble(attributes)
    }
}

/**
 * Pairs each row with its attributes and rebuilds what it can.
 *
 * A source that will not rebuild is dropped, not defaulted: [SourceRecords] has already logged why,
 * and handing the engine a source whose mode is a guess is how a pass does the wrong thing to
 * someone's files.
 */
private fun List<DBSource>.assemble(attributes: List<DBAttribute>): List<SourceEntry> {
    val bySource = attributes.groupBy { it.ownerId }

    return mapNotNull { row ->
        val reader = SourceRecords.Reader(
            bySource[row.id]?.associate { (it.type to it.fieldName) to it.fieldValue }.orEmpty()
        )

        SourceRecords.sourceEntryOf(
            id = row.id,
            deviceId = row.deviceId,
            role = row.role,
            label = row.label,
            originPath = row.originPath,
            createdAtEpochMs = row.createdAtEpochMs,
            lastSyncedAtEpochMs = row.lastSyncedAtEpochMs,
            location = row.location,
            mode = row.mode,
            status = row.status,
            attributes = reader,
        )
    }
}
