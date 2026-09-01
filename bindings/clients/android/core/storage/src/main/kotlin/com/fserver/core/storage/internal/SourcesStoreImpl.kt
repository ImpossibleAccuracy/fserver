package com.fserver.core.storage.internal

import com.fserver.common.model.FileSize
import com.fserver.core.storage.RegisteredSourcesRepository
import com.fserver.core.store.sync.SourcesStore
import com.fserver.core.sync.SourceEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

/**
 * TODO: in-memory scaffolding. Registered sources are lost with the process - swap the
 *  `MutableStateFlow` for the SQLDelight `fileSource` table once the shape settles.
 *  `SourceLocation` and `SyncMode` both need column adapters, which is why this is not written yet.
 *
 * [index] is held so [delete] can drop a source's processed records with it: unregistering must
 * leave no orphan bookkeeping behind. It still touches no bytes on disk.
 */
internal class SourcesStoreImpl(
    private val index: FileIndexStoreImpl,
) : SourcesStore, RegisteredSourcesRepository {
    private val writeLock = Mutex()
    private val state = MutableStateFlow<List<SourceEntry>>(emptyList())

    // ---------------- SourcesStore: what the engine calls ----------------

    override suspend fun all(): List<SourceEntry> = state.value

    override suspend fun findById(id: String): SourceEntry? = state.value.find { it.id == id }

    override suspend fun upsert(source: SourceEntry) = writeLock.withLock {
        state.update { current ->
            val position = current.indexOfFirst { it.id == source.id }
            if (position < 0) listOf(source) + current
            else current.toMutableList().apply { this[position] = source }
        }
    }

    override suspend fun delete(id: String) {
        writeLock.withLock {
            state.update { current -> current.filterNot { it.id == id } }
        }
        index.clearProcessed(id)
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

    // ---------------- RegisteredSourcesRepository: what a screen calls ----------------

    override val sources: Flow<List<SourceEntry>> = state.asStateFlow()

    override fun observeById(id: String): Flow<SourceEntry?> =
        state.map { current -> current.find { it.id == id } }

    override fun observeProcessedCount(id: String): Flow<Int> = index.observeProcessedCount(id)

    override suspend fun rename(id: String, label: String) = writeLock.withLock {
        state.update { current ->
            current.map { source -> if (source.id == id) source.copy(label = label) else source }
        }
    }
}
