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
                    state = file.state.dbName,
                    pinned = if ((file.state as? IndexedFile.State.Present)?.pinned == true) 1 else 0,
                    stateChangedEpochMs = file.state.changedAt?.toEpochMilliseconds(),
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

private enum class RemoteFileState { Present, Evicted, Deleted }

private val IndexedFile.State.dbName: String
    get() = when (this) {
        is IndexedFile.State.Present -> RemoteFileState.Present.name
        is IndexedFile.State.Evicted -> RemoteFileState.Evicted.name
        is IndexedFile.State.Deleted -> RemoteFileState.Deleted.name
    }

/** The one timestamp a state carries, or null for [IndexedFile.State.Present], which carries none. */
private val IndexedFile.State.changedAt: Instant?
    get() = when (this) {
        is IndexedFile.State.Present -> null
        is IndexedFile.State.Evicted -> evictedAt
        is IndexedFile.State.Deleted -> deletedAt
    }

private fun DBRemoteIndexedFile.toDomainModel() = RemoteIndexedFile(
    fileId = fileId,
    path = path,
    state = readState(),
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

/**
 * A row missing the timestamp its state needs reads as [IndexedFile.State.Present] rather than
 * guessing one: an invented `deletedAt` is a deletion this device would go on to propagate.
 */
private fun DBRemoteIndexedFile.readState(): IndexedFile.State {
    val changedAt = stateChangedEpochMs?.let(Instant::fromEpochMilliseconds)

    return when (RemoteFileState.valueOf(state)) {
        RemoteFileState.Present -> IndexedFile.State.Present(pinned = pinned == 1L)

        RemoteFileState.Evicted -> changedAt
            ?.let { IndexedFile.State.Evicted(evictedAt = it) }
            ?: IndexedFile.State.Present()

        RemoteFileState.Deleted -> changedAt
            ?.let { IndexedFile.State.Deleted(deletedAt = it) }
            ?: IndexedFile.State.Present()
    }
}
