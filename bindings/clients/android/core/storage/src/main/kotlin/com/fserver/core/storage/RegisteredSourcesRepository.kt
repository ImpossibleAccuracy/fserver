package com.fserver.core.storage

import com.fserver.core.sync.SourceEntry
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

    fun observeById(id: String): Flow<SourceEntry?>

    /**
     * How many of the source's files are already done, for a progress line. The paths themselves
     * are engine bookkeeping and stay on the store.
     */
    fun observeProcessedCount(id: String): Flow<Int>

    /** Display name only; what the source points at and may do are not the UI's to change. */
    suspend fun rename(id: String, label: String)
}
