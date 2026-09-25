package com.fserver.core.storage

import com.fserver.common.model.FileSize
import com.fserver.core.sync.model.SourceEntry
import kotlinx.coroutines.flow.Flow

/**
 * Registered sources, as a screen needs them. Disjoint from `SourcesStore` on purpose: the engine
 * enumerates sources and marks off what it has worked through, the UI lists and renames.
 *
 * Registering a new source is an engine action - call `SourcesController.addSource`, not this.
 */
interface RegisteredSourcesRepository {
    /** Every registered source, newest first. */
    val sources: Flow<List<SourceEntry>>

    /** Total size of the indexed files still present on this device, across every source. */
    val indexedSize: Flow<FileSize>

    /** Files a peer holds that are not on this device: never fetched, or evicted. */
    val remoteOnly: Flow<FilesTotal>

    fun observeById(id: String): Flow<SourceEntry?>

    /**
     * How many of the source's files are already done, for a progress line. The paths themselves
     * are engine bookkeeping and stay on the store.
     */
    fun observeProcessedCount(id: String): Flow<Int>

    /** Where the source's files stand, counted in the database. */
    fun observeTotals(id: String): Flow<SourceFilesTotals>

    /** Display name only; what the source points at and may do are not the UI's to change. */
    suspend fun rename(id: String, label: String)
}
