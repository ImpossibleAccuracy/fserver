package com.fserver.core.storage.internal

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.fserver.core.storage.database.FServerStorageDatabase
import com.fserver.core.store.sync.SourceRequestsStore
import com.fserver.core.sync.setup.IncomingSourceRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.time.Instant
import com.fserver.core.storage.database.Attribute as DBAttribute
import com.fserver.core.storage.database.SourceRequest as DBSourceRequest

/**
 * Parked requests over the `sourceRequest` row plus the `attribute` rows its mode needs, the same
 * split a source uses. [SourceRecords] owns the translation both ways.
 */
internal class SourceRequestsStoreImpl(
    private val database: FServerStorageDatabase,
) : SourceRequestsStore {
    private val dao = database.sourceRequestQueries
    private val attributeDao = database.attributeQueries

    override fun pending(): Flow<List<IncomingSourceRequest>> = combine(
        dao.selectAll().asFlow().mapToList(Dispatchers.IO),
        attributeDao.selectAllOf(SourceRecords.OwnerRequest).asFlow().mapToList(Dispatchers.IO),
    ) { rows, attributes -> rows.assemble(attributes) }

    override suspend fun findById(sourceId: String): IncomingSourceRequest? {
        val row = dao.selectById(sourceId).executeAsOneOrNull() ?: return null

        val attributes = attributeDao
            .selectByOwner(owner = SourceRecords.OwnerRequest, ownerId = sourceId)
            .executeAsList()

        return listOf(row).assemble(attributes).firstOrNull()
    }

    /**
     * Row first, then attributes, and the old attributes dropped in between: `INSERT OR REPLACE`
     * leaves whatever the previous mode wrote, and a narrower one would inherit fields it does not
     * own.
     */
    override suspend fun upsert(request: IncomingSourceRequest) {
        database.transaction {
            dao.upsert(
                sourceId = request.sourceId,
                deviceId = request.deviceId,
                label = request.label,
                mode = SourceRecords.discriminatorOf(request.syncMode),
                receivedAtEpochMs = request.receivedAt.toEpochMilliseconds(),
            )

            attributeDao.deleteByOwnerId(
                owner = SourceRecords.OwnerRequest,
                ownerId = request.sourceId,
            )

            for (attribute in SourceRecords.modeAttributesOf(request.syncMode)) {
                attributeDao.insert(
                    owner = SourceRecords.OwnerRequest,
                    ownerId = request.sourceId,
                    type = attribute.type,
                    fieldName = attribute.field,
                    fieldValue = attribute.value,
                )
            }
        }
    }

    override suspend fun delete(sourceId: String) {
        database.transaction {
            // The trigger on `sourceRequest` covers this too; done here as well so the rows go even
            // if a migration rebuilt the table and dropped its triggers with it.
            attributeDao.deleteByOwnerId(
                owner = SourceRecords.OwnerRequest,
                ownerId = sourceId,
            )
            dao.delete(sourceId)
        }
    }
}

/**
 * A request whose mode will not rebuild is dropped, not defaulted: accepting it would register a
 * source running under a mode nobody asked for.
 */
private fun List<DBSourceRequest>.assemble(
    attributes: List<DBAttribute>,
): List<IncomingSourceRequest> {
    val byRequest = attributes.groupBy { it.ownerId }

    return mapNotNull { row ->
        val reader = SourceRecords.Reader(
            byRequest[row.sourceId]
                ?.associate { (it.type to it.fieldName) to it.fieldValue }
                .orEmpty()
        )

        val mode = SourceRecords.modeOf(row.sourceId, row.mode, reader) ?: return@mapNotNull null

        IncomingSourceRequest(
            sourceId = row.sourceId,
            deviceId = row.deviceId,
            label = row.label,
            syncMode = mode,
            receivedAt = Instant.fromEpochMilliseconds(row.receivedAtEpochMs),
        )
    }
}
