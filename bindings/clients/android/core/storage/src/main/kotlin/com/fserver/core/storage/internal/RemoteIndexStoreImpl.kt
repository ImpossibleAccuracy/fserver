package com.fserver.core.storage.internal

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.fserver.common.model.ContentHash
import com.fserver.common.model.FileSize
import com.fserver.core.storage.FilesTotal
import com.fserver.core.storage.database.FServerStorageDatabase
import com.fserver.core.storage.database.RemoteIndexedFileVersion
import com.fserver.core.store.sync.RemoteIndexStore
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.index.RemoteIndexedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlin.time.Instant
import com.fserver.core.storage.database.RemoteIndexedFile as DBRemoteIndexedFile

internal class RemoteIndexStoreImpl(
    private val database: FServerStorageDatabase,
) : RemoteIndexStore {
    private val dao = database.remoteIndexedFileQueries
    private val versions = database.remoteIndexedFileVersionQueries

    /** Re-read on either table, as [FileIndexStoreImpl.all] does. */
    override val all: Flow<List<RemoteIndexedFile>> = combine(
        dao.selectAll().asFlow().mapToList(Dispatchers.IO),
        versions.selectAll().asFlow().mapToList(Dispatchers.IO),
    ) { rows, vectors -> rows.withVectors(vectors) }

    fun observeRemoteOnly(): Flow<FilesTotal> = dao.remoteOnlyTotals()
        .asFlow()
        .mapToOne(Dispatchers.IO)
        .map { FilesTotal(count = it.files.toInt(), size = FileSize(it.bytes)) }

    override suspend fun files(sourceId: String): List<RemoteIndexedFile> =
        database.transactionWithResult {
            dao.selectBySource(sourceId).executeAsList()
                .withVectors(versions.selectBySource(sourceId).executeAsList())
        }

    override suspend fun findFile(key: IndexedFileKey): RemoteIndexedFile? =
        database.transactionWithResult {
            dao.findByKey(sourceId = key.sourceId, fileId = key.fileId)
                .executeAsOneOrNull()
                ?.let { row ->
                    listOf(row).withVectors(versions.selectByKey(sourceId = key.sourceId, fileId = key.fileId).executeAsList())
                        .single()
                }
        }

    /**
     * Delete then insert, in one transaction: a reader that caught the gap would see the peer
     * holding nothing, which is indistinguishable from a peer that deleted everything.
     */
    override suspend fun replace(
        sourceId: String,
        deviceId: String,
        files: Collection<RemoteIndexedFile>,
    ) {
        database.transaction {
            dao.deleteBySource(sourceId)

            // The delete cascaded to the vectors, so none is left to clear per file.
            for (file in files) {
                insert(sourceId, deviceId, file, fresh = true)
            }
        }
    }

    override suspend fun upsert(deviceId: String, file: RemoteIndexedFile) {
        database.transaction { insert(file.sourceId, deviceId, file) }
    }

    private fun insert(sourceId: String, deviceId: String, file: RemoteIndexedFile, fresh: Boolean = false) {
        dao.insert(
            sourceId = sourceId,
            fileId = file.fileId,
            deviceId = deviceId,
            path = file.path,
            state = FileStates.nameOf(file.state),
            pinned = FileStates.pinnedOf(file.state),
            stateChangedEpochMs = FileStates.changedAtOf(file.state),
            size = file.size.bytes,
            modifiedAtEpochMs = file.modifiedAt.toEpochMilliseconds(),
            hashValue = file.hash?.value,
            hashAlgorithm = file.hash?.algorithm,
            hlc = file.version?.hlc?.packed,
            originDevice = file.version?.originDevice,
            seenAtEpochMs = file.seenAt.toEpochMilliseconds(),
        )

        if (!fresh) versions.deleteByKey(sourceId = sourceId, fileId = file.fileId)
        file.version?.vector?.counters?.forEach { (device, counter) ->
            versions.insert(sourceId = sourceId, fileId = file.fileId, deviceId = device, counter = counter)
        }
    }

    override suspend fun clear(sourceId: String) {
        dao.deleteBySource(sourceId)
    }
}

private fun List<DBRemoteIndexedFile>.withVectors(
    vectors: List<RemoteIndexedFileVersion>,
): List<RemoteIndexedFile> {
    val counters = FileVersions.group(vectors, { it.sourceId to it.fileId }, { it.deviceId to it.counter })
    return map { it.toDomainModel(counters[it.sourceId to it.fileId].orEmpty()) }
}

private fun DBRemoteIndexedFile.toDomainModel(counters: Map<String, Long>) = RemoteIndexedFile(
    sourceId = sourceId,
    fileId = fileId,
    path = path,
    state = FileStates.read(
        state = state,
        pinned = pinned,
        changedAtEpochMs = stateChangedEpochMs,
    ),
    size = FileSize(size),
    modifiedAt = Instant.fromEpochMilliseconds(modifiedAtEpochMs),
    hash = hashValue?.let { value ->
        hashAlgorithm?.let { ContentHash(value = value, algorithm = it) }
    },
    version = FileVersions.read(hlc = hlc, originDevice = originDevice, counters = counters),
    seenAt = Instant.fromEpochMilliseconds(seenAtEpochMs),
)
