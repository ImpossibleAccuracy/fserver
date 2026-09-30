package com.fserver.core.storage.internal

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.fserver.core.files.SourceLocation
import com.fserver.core.storage.OneShotTransfersRepository
import com.fserver.core.storage.database.FServerStorageDatabase
import com.fserver.core.store.oneshot.OneShotTransfersStore
import com.fserver.core.oneshot.model.OneShotTransfer
import com.fserver.core.oneshot.model.OneShotTransferFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import timber.log.Timber
import kotlin.time.Instant
import com.fserver.core.storage.database.Attribute as DBAttribute
import com.fserver.core.storage.database.OneShotTransfer as DBOneShotTransfer
import com.fserver.core.storage.database.OneShotTransferFile as DBOneShotTransferFile

/**
 * `transfer` + `transferFile` rows, with an incoming destination in `attribute` rows through
 * [SourceRecords]. "Finished is final" is enforced in the SQL, so a race between a cancel and the
 * engine's last write cannot resurrect a transfer.
 */
internal class OneShotTransfersStoreImpl(
    private val database: FServerStorageDatabase,
) : OneShotTransfersStore, OneShotTransfersRepository {
    private val dao = database.oneShotTransferQueries
    private val attributeDao = database.attributeQueries

    override val transfers: Flow<List<OneShotTransfer>> = combine(
        dao.selectAll().asFlow().mapToList(Dispatchers.IO),
        dao.selectAllFiles().asFlow().mapToList(Dispatchers.IO),
        attributeDao.selectAllOf(SourceRecords.OwnerOneShotTransfer).asFlow().mapToList(Dispatchers.IO),
        ::assemble,
    )

    override fun observe(id: String): Flow<OneShotTransfer?> = combine(
        dao.selectById(id).asFlow().mapToList(Dispatchers.IO),
        dao.selectFilesOf(listOf(id)).asFlow().mapToList(Dispatchers.IO),
        attributeDao.selectByOwner(SourceRecords.OwnerOneShotTransfer, id).asFlow().mapToList(Dispatchers.IO),
        ::assemble,
    ).map { it.firstOrNull() }

    override suspend fun find(id: String): OneShotTransfer? {
        val row = dao.selectById(id).executeAsOneOrNull() ?: return null

        return load(listOf(row)).firstOrNull()
    }

    override suspend fun unfinished(): List<OneShotTransfer> =
        load(dao.selectUnfinished().executeAsList())

    override suspend fun insert(transfer: OneShotTransfer): Boolean = database.transactionWithResult {
        val destination = (transfer.direction as? OneShotTransfer.Direction.Incoming)?.destination

        val inserted = dao.insert(
            id = transfer.id,
            peerDeviceId = transfer.peer.deviceId,
            peerName = transfer.peer.displayName,
            direction = OneShotTransferRecords.discriminatorOf(transfer.direction),
            destination = destination?.let { SourceRecords.discriminatorOf(it) },
            status = OneShotTransferRecords.discriminatorOf(transfer.status),
            failureReason = (transfer.status as? OneShotTransfer.Status.Failed)?.reason,
            createdAtEpochMs = transfer.createdAt.toEpochMilliseconds(),
            finishedAtEpochMs = transfer.finishedAt?.toEpochMilliseconds(),
        ).value > 0

        if (!inserted) return@transactionWithResult false

        destination?.let { writeDestination(transfer.id, it) }

        for (file in transfer.files) {
            dao.insertFile(
                transferId = transfer.id,
                position = file.index.toLong(),
                name = file.name,
                size = file.size,
                locator = file.locator,
                committedBytes = file.committedBytes,
                status = OneShotTransferRecords.discriminatorOf(file.status),
                failureReason = (file.status as? OneShotTransferFile.Status.Failed)?.reason,
            )
        }

        true
    }

    override suspend fun updateStatus(id: String, status: OneShotTransfer.Status, at: Instant): Boolean {
        return dao.updateStatus(
            status = OneShotTransferRecords.discriminatorOf(status),
            failureReason = (status as? OneShotTransfer.Status.Failed)?.reason,
            finishedAtEpochMs = at.toEpochMilliseconds().takeIf { status.isFinished },
            id = id,
        ).value > 0
    }

    override suspend fun accept(
        id: String,
        destination: SourceLocation.Hostable,
    ): Boolean = database.transactionWithResult {
        val accepted = dao.accept(
            destination = SourceRecords.discriminatorOf(destination),
            id = id,
        ).value > 0

        if (accepted) writeDestination(id, destination)

        accepted
    }

    override suspend fun updateFile(transferId: String, file: OneShotTransferFile) {
        dao.updateFile(
            locator = file.locator,
            committedBytes = file.committedBytes,
            status = OneShotTransferRecords.discriminatorOf(file.status),
            failureReason = (file.status as? OneShotTransferFile.Status.Failed)?.reason,
            transferId = transferId,
            position = file.index.toLong(),
        )
    }

    override suspend fun checkpoint(transferId: String, index: Int, committedBytes: Long) {
        dao.checkpoint(
            committedBytes = committedBytes,
            transferId = transferId,
            position = index.toLong(),
        )
    }

    override suspend fun delete(id: String): Boolean = dao.deleteFinished(id).value > 0

    override suspend fun clearFinished() {
        dao.deleteAllFinished()
    }

    private fun writeDestination(id: String, destination: SourceLocation.Hostable) {
        attributeDao.deleteByOwnerId(owner = SourceRecords.OwnerOneShotTransfer, ownerId = id)

        for (attribute in SourceRecords.locationAttributesOf(destination)) {
            attributeDao.insert(
                owner = SourceRecords.OwnerOneShotTransfer,
                ownerId = id,
                type = attribute.type,
                fieldName = attribute.field,
                fieldValue = attribute.value,
            )
        }
    }

    private fun load(rows: List<DBOneShotTransfer>): List<OneShotTransfer> {
        if (rows.isEmpty()) return emptyList()

        val ids = rows.map { it.id }

        return assemble(
            rows = rows,
            files = dao.selectFilesOf(ids).executeAsList(),
            attributes = attributeDao.selectByOwnerIds(SourceRecords.OwnerOneShotTransfer, ids).executeAsList(),
        )
    }
}

/** Rows that will not rebuild are skipped and logged, never defaulted - see [SourceRecords]. */
private fun assemble(
    rows: List<DBOneShotTransfer>,
    files: List<DBOneShotTransferFile>,
    attributes: List<DBAttribute>,
): List<OneShotTransfer> {
    val filesByTransfer = files.groupBy { it.transferId }
    val attributesByTransfer = attributes.groupBy { it.ownerId }

    return rows.mapNotNull { row ->
        val reader = SourceRecords.Reader(
            attributesByTransfer[row.id]
                ?.associate { (it.type to it.fieldName) to it.fieldValue }
                .orEmpty()
        )

        runCatching { OneShotTransferRecords.transferOf(row, filesByTransfer[row.id].orEmpty(), reader) }
            .onFailure { Timber.w(it, "Skipping transfer ${row.id}") }
            .getOrNull()
    }
}
