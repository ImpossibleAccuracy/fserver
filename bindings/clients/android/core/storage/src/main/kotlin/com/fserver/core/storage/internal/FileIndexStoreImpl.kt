package com.fserver.core.storage.internal

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import com.fserver.common.model.ContentHash
import com.fserver.common.model.FileSize
import com.fserver.core.storage.database.FServerStorageDatabase
import com.fserver.core.store.sync.FileIndexStore
import com.fserver.core.sync.index.IndexedFile
import com.fserver.core.sync.index.IndexedFileKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant
import com.fserver.core.storage.database.IndexedFile as DBIndexedFile

/**
 * What this device has already handed off, per source. [RemoteIndexStoreImpl] is the same
 * bookkeeping for what the peer reported, and both encode a file's state through [FileStates].
 *
 * Nothing here touches bytes on disk: a row says a handoff happened, never that a file is readable.
 */
internal class FileIndexStoreImpl(
    private val database: FServerStorageDatabase,
) : FileIndexStore {
    private val dao = database.indexedFileQueries

    override suspend fun findFile(key: IndexedFileKey): IndexedFile? =
        dao.findByKey(sourceId = key.sourceId, fileId = key.fileId)
            .executeAsOneOrNull()
            ?.toDomainModel()

    override suspend fun processedFiles(sourceId: String): List<IndexedFile> =
        dao.selectBySource(sourceId).executeAsList().map { it.toDomainModel() }

    /** One transaction: a pass that died halfway through must not leave half its files marked done. */
    override suspend fun markProcessed(indexed: Collection<IndexedFile>) {
        database.transaction {
            for (file in indexed) {
                dao.upsert(
                    id = file.id,
                    sourceId = file.sourceId,
                    fileId = file.fileId,
                    path = file.path,
                    locator = file.locator,
                    state = FileStates.nameOf(file.state),
                    pinned = FileStates.pinnedOf(file.state),
                    stateChangedEpochMs = FileStates.changedAtOf(file.state),
                    size = file.size.bytes,
                    modifiedAtEpochMs = file.modifiedAt.toEpochMilliseconds(),
                    hashValue = file.hash?.value,
                    hashAlgorithm = file.hash?.algorithm,
                    revisionOriginDevice = file.revision?.originDevice,
                    revisionCounter = file.revision?.counter,
                    processedAtEpochMs = file.processedAt.toEpochMilliseconds(),
                )
            }
        }
    }

    override suspend fun saveHash(key: IndexedFileKey, hash: ContentHash) {
        dao.updateHash(
            hashValue = hash.value,
            hashAlgorithm = hash.algorithm,
            sourceId = key.sourceId,
            fileId = key.fileId,
        )
    }

    override suspend fun updateFileState(key: IndexedFileKey, state: IndexedFile.State) {
        dao.updateState(
            state = FileStates.nameOf(state),
            pinned = FileStates.pinnedOf(state),
            stateChangedEpochMs = FileStates.changedAtOf(state),
            sourceId = key.sourceId,
            fileId = key.fileId,
        )
    }

    /**
     * Row by row inside one transaction rather than a single `IN`: the keys may span sources, and a
     * statement matching fileId alone would move a file of another source into the same state.
     */
    override suspend fun updateStateBatch(keys: List<IndexedFileKey>, state: IndexedFile.State) {
        val name = FileStates.nameOf(state)
        val pinned = FileStates.pinnedOf(state)
        val changedAt = FileStates.changedAtOf(state)

        database.transaction {
            for (key in keys) {
                dao.updateState(
                    state = name,
                    pinned = pinned,
                    stateChangedEpochMs = changedAt,
                    sourceId = key.sourceId,
                    fileId = key.fileId,
                )
            }
        }
    }

    override suspend fun clearProcessed(sourceId: String) {
        dao.deleteBySource(sourceId)
    }

    /** For the progress line a screen shows. Not on the SPI - the engine never asks for a count. */
    fun observeProcessedCount(sourceId: String): Flow<Int> = dao.countBySource(sourceId)
        .asFlow()
        .mapToOne(Dispatchers.IO)
        .map { it.toInt() }
}

private fun DBIndexedFile.toDomainModel() = IndexedFile(
    id = id,
    sourceId = sourceId,
    fileId = fileId,
    path = path,
    locator = locator,
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
    processedAt = Instant.fromEpochMilliseconds(processedAtEpochMs),
)
