package com.fserver.core.storage.internal

import com.fserver.common.model.ContentHash
import com.fserver.core.store.sync.FileIndexStore
import com.fserver.core.sync.index.IndexedFile
import com.fserver.core.sync.index.IndexedFileKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * TODO: in-memory scaffolding. Processed sets are lost with the process - swap the
 *  `MutableStateFlow` for a SQLDelight `indexedFile` table keyed by [IndexedFile.id] with a
 *  source-id index once the shape settles. `ContentHash` needs a column adapter, which is why
 *  this is not written yet.
 */
internal class FileIndexStoreImpl : FileIndexStore {
    private val writeLock = Mutex()

    private val processed = MutableStateFlow<List<IndexedFile>>(emptyList())

    override suspend fun findFile(key: IndexedFileKey): IndexedFile? {
        return processed.value.find { it.fileId == key.fileId && it.sourceId == key.sourceId }
    }

    override suspend fun processedFiles(sourceId: String): List<IndexedFile> =
        processed.value.filter { it.sourceId == sourceId }

    override suspend fun markProcessed(
        indexed: Collection<IndexedFile>,
    ) {
        processed.update { current ->
            val byId =
                current.associateByTo(mutableMapOf()) { IndexedFileKey(it.fileId, it.sourceId) }
            indexed.forEach { file ->
                val key = IndexedFileKey(file.fileId, file.sourceId)
                byId[key] = file
            }
            byId.values.toList()
        }
    }

    override suspend fun saveHash(
        key: IndexedFileKey,
        hash: ContentHash
    ) {
        processed.update { current ->
            current.map { file ->
                if (file.fileId == key.fileId && file.sourceId == key.sourceId) {
                    file.copy(hash = hash)
                } else {
                    file
                }
            }
        }
    }

    override suspend fun updateFileState(
        key: IndexedFileKey,
        state: IndexedFile.State
    ) {
        processed.update { current ->
            current.map { file ->
                if (file.fileId == key.fileId && file.sourceId == key.sourceId) {
                    file.copy(state = state)
                } else {
                    file
                }
            }
        }
    }

    override suspend fun updateStateBatch(
        keys: List<IndexedFileKey>,
        state: IndexedFile.State
    ) {
        processed.update { current ->
            current.map { file ->
                if (keys.any { it.fileId == file.fileId && it.sourceId == file.sourceId }) {
                    file.copy(state = state)
                } else {
                    file
                }
            }
        }
    }

    override suspend fun clearProcessed(sourceId: String) = writeLock.withLock {
        processed.update { current ->
            current.filterNot { it.sourceId == sourceId }
        }
    }

    /** For the progress line a screen shows. Not on the SPI - the engine never asks for a count. */
    fun observeProcessedCount(sourceId: String): Flow<Int> =
        processed.map { current -> current.count { it.sourceId == sourceId } }
}
