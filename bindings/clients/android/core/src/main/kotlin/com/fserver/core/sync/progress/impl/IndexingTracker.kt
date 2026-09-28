package com.fserver.core.sync.progress.impl

import com.fserver.core.sync.progress.IndexingProgress
import com.fserver.core.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** The newest indexing run per source. */
internal class IndexingTracker(
    private val timeProvider: TimeProvider,
) {
    private val state = MutableStateFlow<Map<String, IndexingProgress>>(emptyMap())

    fun indexing(sourceId: String): Flow<IndexingProgress?> =
        state.map { it[sourceId] }.distinctUntilChanged()

    fun clearFinished() {
        state.update { runs -> runs.filterValues { !it.isFinished } }
    }

    fun started(sourceId: String) {
        val now = timeProvider.now()

        state.update {
            it + (sourceId to IndexingProgress(
                sourceId = sourceId,
                stage = IndexingProgress.Stage.Scanning,
                startedAt = now,
                updatedAt = now,
            ))
        }
    }

    fun scanned(sourceId: String, files: Int, bytes: Long) =
        update(sourceId) { it.copy(filesScanned = files, bytesScanned = bytes) }

    fun hashing(sourceId: String, done: Int, total: Int) = update(sourceId) {
        it.copy(stage = IndexingProgress.Stage.Hashing, filesHashed = done, filesToHash = total)
    }

    fun finished(sourceId: String, failed: Boolean) = update(sourceId) {
        it.copy(stage = if (failed) IndexingProgress.Stage.Failed else IndexingProgress.Stage.Finished)
    }

    private fun update(sourceId: String, transform: (IndexingProgress) -> IndexingProgress) {
        state.update { runs ->
            val existing = runs[sourceId] ?: return@update runs
            runs + (sourceId to transform(existing).copy(updatedAt = timeProvider.now()))
        }
    }
}
