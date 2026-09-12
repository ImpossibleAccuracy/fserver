package com.fserver.core.storage.internal

import com.fserver.common.model.ContentHash
import com.fserver.common.model.FileSize
import com.fserver.core.storage.database.FServerStorageDatabase
import com.fserver.core.store.sync.RemoteIndexStore
import com.fserver.core.sync.index.IndexedFile
import com.fserver.core.sync.index.RemoteIndexedFile
import kotlin.time.Instant
import com.fserver.core.storage.database.RemoteIndexedFile as DBRemoteIndexedFile

internal class RemoteIndexStoreImpl(
    private val database: FServerStorageDatabase,
) : RemoteIndexStore {
    private val dao = database.remoteIndexedFileQueries

    override suspend fun files(sourceId: String): List<RemoteIndexedFile> =
        dao.selectBySource(sourceId).executeAsList().map { it.toDomainModel() }

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

            for (file in files) {
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
                    revisionOriginDevice = file.revision?.originDevice,
                    revisionCounter = file.revision?.counter,
                    seenAtEpochMs = file.seenAt.toEpochMilliseconds(),
                )
            }
        }
    }

    override suspend fun clear(sourceId: String) {
        dao.deleteBySource(sourceId)
    }
}

private fun DBRemoteIndexedFile.toDomainModel() = RemoteIndexedFile(
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
    revision = revisionOriginDevice?.let { origin ->
        revisionCounter?.let { IndexedFile.Revision(originDevice = origin, counter = it) }
    },
    seenAt = Instant.fromEpochMilliseconds(seenAtEpochMs),
)
