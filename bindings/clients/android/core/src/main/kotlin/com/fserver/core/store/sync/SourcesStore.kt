package com.fserver.core.store.sync

import com.fserver.core.store.FServerStorageApi
import com.fserver.core.sync.SourceEntry
import com.fserver.core.sync.index.IndexedFile
import kotlin.time.Instant

/**
 * The registered sources, as the engine needs them: enumerate them to work through, stamp what a
 * pass found.
 *
 * Disjoint from `RegisteredSourcesRepository` on purpose - the engine registers and marks off, the
 * UI lists and renames. See [com.fserver.core.store.FServerStorageApi].
 */
@SubclassOptInRequired(FServerStorageApi::class)
interface SourcesStore {
    /** Every registered source. Read on each periodic pass, so it must be cheap. */
    suspend fun all(): List<SourceEntry>

    suspend fun findById(id: String): SourceEntry?

    /** Inserts, or replaces the record carrying the same [SourceEntry.id]. */
    suspend fun upsert(source: SourceEntry)

    /** Drops the source and every [IndexedFile] recorded against it. */
    suspend fun delete(id: String)

    /** Stamps what a completed pass found, leaving the rest of the record alone. */
    suspend fun recordScanResult(id: String, fileCount: Int, totalBytes: Long, at: Instant)
}
