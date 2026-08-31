package com.fserver.core.storage.internal

import com.fserver.common.model.FileSize
import com.fserver.core.files.source.FileSource
import com.fserver.core.files.source.ProcessedFile
import com.fserver.core.storage.FileSourcesRepository
import com.fserver.core.store.FileSourcesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

/**
 * TODO: in-memory scaffolding. Registered sources and their processed sets are lost with the
 *  process - swap both `MutableStateFlow`s for SQLDelight tables (`FileSource.sq`, plus a
 *  `processedFile` table keyed by `ProcessedFile.id` with a source-id index) once the shape
 *  settles. `ScanSource` and `FileHash` both need column adapters, which is why this is not
 *  written yet.
 */
internal class FileSourcesStoreImpl : FileSourcesStore, FileSourcesRepository {
    private val writeLock = Mutex()
    private val state = MutableStateFlow<List<FileSource>>(emptyList())

    /** Source id -> that source's processed records, keyed by [ProcessedFile.id]. */
    private val processed =
        MutableStateFlow<Map<String, Map<String, ProcessedFile>>>(emptyMap())

    // ---------------- FileSourcesStore: what the engine calls ----------------

    override suspend fun all(): List<FileSource> = state.value

    override suspend fun findById(id: String): FileSource? = state.value.find { it.id == id }

    override suspend fun upsert(source: FileSource) = writeLock.withLock {
        state.update { current ->
            val index = current.indexOfFirst { it.id == source.id }
            if (index < 0) listOf(source) + current
            else current.toMutableList().apply { this[index] = source }
        }
    }

    override suspend fun delete(id: String) = writeLock.withLock {
        state.update { current -> current.filterNot { it.id == id } }
        processed.update { current -> current - id }
    }

    override suspend fun recordScanResult(
        id: String,
        fileCount: Int,
        totalBytes: Long,
        at: Instant,
    ) = writeLock.withLock {
        state.update { current ->
            current.map { source ->
                if (source.id != id) source
                else source.copy(
                    fileCount = fileCount,
                    totalSize = FileSize(totalBytes),
                    lastSyncedAt = at,
                )
            }
        }
    }

    override suspend fun processedFiles(sourceId: String): List<ProcessedFile> =
        processed.value[sourceId].orEmpty().values.toList()

    override suspend fun findProcessed(sourceId: String, path: String): ProcessedFile? =
        processed.value[sourceId]?.get(ProcessedFile.idOf(sourceId, path))

    override suspend fun markProcessed(files: Collection<ProcessedFile>) = writeLock.withLock {
        if (files.isEmpty()) return@withLock

        processed.update { current ->
            // One source at a time is the common case, but a caller may hand in a mixed batch.
            files.groupBy { it.sourceId }.entries.fold(current) { acc, (sourceId, batch) ->
                acc + (sourceId to acc[sourceId].orEmpty() + batch.associateBy { it.id })
            }
        }
    }

    override suspend fun clearProcessed(sourceId: String) = writeLock.withLock {
        processed.update { current -> current - sourceId }
    }

    // ---------------- FileSourcesRepository: what a screen calls ----------------

    override val sources: Flow<List<FileSource>> = state.asStateFlow()

    override fun observeById(id: String): Flow<FileSource?> =
        state.map { current -> current.find { it.id == id } }

    override fun observeProcessedCount(id: String): Flow<Int> =
        processed.map { current -> current[id].orEmpty().size }

    override suspend fun rename(id: String, label: String) = writeLock.withLock {
        state.update { current ->
            current.map { source -> if (source.id == id) source.copy(label = label) else source }
        }
    }
}
