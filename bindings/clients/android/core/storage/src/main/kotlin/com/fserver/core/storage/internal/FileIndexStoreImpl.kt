package com.fserver.core.storage.internal

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.fserver.common.model.ContentHash
import com.fserver.common.model.FileSize
import com.fserver.core.crypto.model.AtRest
import com.fserver.core.storage.database.FServerStorageDatabase
import com.fserver.core.storage.database.IndexedFileVersion
import com.fserver.core.store.sync.FileIndexStore
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.limits.SourceUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    private val versions = database.indexedFileVersionQueries

    /** Re-read on either table: a vector can change without its file row changing. */
    override val all: Flow<List<LocalIndexedFile>> = combine(
        dao.selectAll().asFlow().mapToList(Dispatchers.IO),
        versions.selectAll().asFlow().mapToList(Dispatchers.IO),
    ) { rows, vectors -> rows.withVectors(vectors) }

    override suspend fun findFile(key: IndexedFileKey): LocalIndexedFile? =
        database.transactionWithResult {
            dao.findByKey(sourceId = key.sourceId, fileId = key.fileId)
                .executeAsOneOrNull()
                ?.let { row ->
                    val vector = versions.selectByKey(sourceId = key.sourceId, fileId = key.fileId)
                        .executeAsList()
                    listOf(row).withVectors(vector).single()
                }
        }

    override suspend fun findByLocator(sourceId: String, locator: String): LocalIndexedFile? =
        database.transactionWithResult {
            dao.findByLocator(sourceId = sourceId, locator = locator)
                .executeAsOneOrNull()
                ?.let { row ->
                    val vector = versions.selectByKey(sourceId = sourceId, fileId = row.fileId).executeAsList()
                    listOf(row).withVectors(vector).single()
                }
        }

    override suspend fun processedFiles(sourceId: String): List<LocalIndexedFile> =
        database.transactionWithResult {
            dao.selectBySource(sourceId).executeAsList()
                .withVectors(versions.selectBySource(sourceId).executeAsList())
        }

    override suspend fun presentUsage(sourceId: String): SourceUsage =
        dao.presentUsageBySource(sourceId).executeAsOne().let {
            SourceUsage(files = it.files.toInt(), bytes = it.bytes)
        }

    /** One transaction: a pass that died halfway through must not leave half its files marked done. */
    override suspend fun markProcessed(indexed: Collection<LocalIndexedFile>) {
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
                    hashStale = if (file.hashStale) 1 else 0,
                    hlc = file.version?.hlc?.packed,
                    originDevice = file.version?.originDevice,
                    processedAtEpochMs = file.processedAt.toEpochMilliseconds(),
                    atRestCipher = (file.atRest as? AtRest.Sealed)?.cipherId,
                    atRestKey = (file.atRest as? AtRest.Sealed)?.keyId,
                )

                versions.deleteByKey(sourceId = file.sourceId, fileId = file.fileId)
                file.version?.vector?.counters?.forEach { (deviceId, counter) ->
                    versions.insert(
                        sourceId = file.sourceId,
                        fileId = file.fileId,
                        deviceId = deviceId,
                        counter = counter,
                    )
                }
            }
        }
    }

    override suspend fun updateFileState(key: IndexedFileKey, state: LocalIndexedFile.State) {
        dao.updateState(
            state = FileStates.nameOf(state),
            pinned = FileStates.pinnedOf(state),
            stateChangedEpochMs = FileStates.changedAtOf(state),
            sourceId = key.sourceId,
            fileId = key.fileId,
        )
    }

    override suspend fun clearProcessed(sourceId: String) {
        dao.deleteBySource(sourceId)
    }

    /** For the progress line a screen shows. Not on the SPI - the engine never asks for a count. */
    fun observeProcessedCount(sourceId: String): Flow<Int> = dao.countBySource(sourceId)
        .asFlow()
        .mapToOne(Dispatchers.IO)
        .map { it.toInt() }

    /** For the storage line a screen shows. Not on the SPI either. */
    fun observePresentSize(): Flow<Long> = dao.sizeOfPresent()
        .asFlow()
        .mapToOne(Dispatchers.IO)
}

private fun List<DBIndexedFile>.withVectors(vectors: List<IndexedFileVersion>): List<LocalIndexedFile> {
    val counters = FileVersions.group(vectors, { it.sourceId to it.fileId }, { it.deviceId to it.counter })
    return map { it.toDomainModel(counters[it.sourceId to it.fileId].orEmpty()) }
}

private fun DBIndexedFile.toDomainModel(counters: Map<String, Long>) = LocalIndexedFile(
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
    version = FileVersions.read(hlc = hlc, originDevice = originDevice, counters = counters),
    hashStale = hashStale == 1L,
    processedAt = Instant.fromEpochMilliseconds(processedAtEpochMs),
    atRest = if (atRestCipher != null && atRestKey != null) AtRest.Sealed(atRestCipher, atRestKey) else AtRest.Plain,
)
