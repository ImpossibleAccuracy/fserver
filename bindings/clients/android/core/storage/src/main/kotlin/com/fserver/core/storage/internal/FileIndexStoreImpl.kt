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

    /** Source id -> that source's processed records, keyed by [IndexedFile.id]. */
    private val processed = MutableStateFlow<Map<String, Map<String, IndexedFile>>>(emptyMap())

    override suspend fun processedFiles(sourceId: String): List<IndexedFile> =
        processed.value[sourceId].orEmpty().values.toList()

    override suspend fun markProcessed(
        indexed: Collection<IndexedFile>,
    ) {
        processed.update { current ->
            indexed
                .groupBy { it.sourceId }
                .entries
                .fold(current) { acc, (sourceId, batch) ->
                    val updated = acc[sourceId].orEmpty().plus(batch.associateBy { it.id })
                    acc + (sourceId to updated)
                }
        }
    }

    override suspend fun saveHash(
        fileId: IndexedFileKey,
        hash: ContentHash
    ) {
        processed.update { current ->
            current.mapValues { (_, files) ->
                files.mapValues { (_, file) ->
                    if (file.fileId == fileId.fileId && file.sourceId == fileId.sourceId) {
                        file.copy(hash = hash)
                    } else {
                        file
                    }
                }
            }
        }
    }

    override suspend fun updateFileState(
        fileId: IndexedFileKey,
        state: IndexedFile.State
    ) {
        processed.update { current ->
            current.mapValues { (_, files) ->
                files.mapValues { (_, file) ->
                    if (file.fileId == fileId.fileId && file.sourceId == fileId.sourceId) {
                        file.copy(state = state)
                    } else {
                        file
                    }
                }
            }
        }
    }

    override suspend fun updateStateBatch(
        fileIds: List<IndexedFileKey>,
        state: IndexedFile.State
    ) {
        processed.update { current ->
            current.mapValues { (_, files) ->
                files.mapValues { (_, file) ->
                    val matching =
                        fileIds.any { it.fileId == file.fileId && it.sourceId == file.sourceId }

                    if (matching) {
                        file.copy(state = state)
                    } else {
                        file
                    }
                }
            }
        }
    }

    override suspend fun clearProcessed(sourceId: String) = writeLock.withLock {
        processed.update { current -> current - sourceId }
    }

    /** For the progress line a screen shows. Not on the SPI - the engine never asks for a count. */
    fun observeProcessedCount(sourceId: String): Flow<Int> =
        processed.map { current -> current[sourceId].orEmpty().size }
}
