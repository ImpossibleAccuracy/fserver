package com.fserver.core.store

import com.fserver.core.files.source.FileSource
import com.fserver.core.files.source.ProcessedFile
import kotlin.time.Instant

/**
 * Registered sources and what each has already worked through, as the engine needs them.
 *
 * Disjoint from the repository on purpose - the engine enumerates sources to work through them and
 * marks off what it finished, the UI lists and renames them. See [FServerStorageApi].
 */
@SubclassOptInRequired(FServerStorageApi::class)
interface FileSourcesStore {
    /** Every registered source. Read on each periodic pass, so it must be cheap. */
    suspend fun all(): List<FileSource>

    suspend fun findById(id: String): FileSource?

    /** Inserts, or replaces the record carrying the same [FileSource.id]. */
    suspend fun upsert(source: FileSource)

    /** Drops the source and every [ProcessedFile] recorded against it. */
    suspend fun delete(id: String)

    /** Stamps what a completed pass found, leaving the rest of the record alone. */
    suspend fun recordScanResult(id: String, fileCount: Int, totalBytes: Long, at: Instant)

    // ---------------- What a source has already worked through ----------------

    /**
     * Everything [sourceId] has already handed off, so the next pass can diff against it.
     *
     * Read once per pass and compared against the whole scan, so it must tolerate being large.
     */
    suspend fun processedFiles(sourceId: String): List<ProcessedFile>

    /** null when this pass is the first to see [path]. */
    suspend fun findProcessed(sourceId: String, path: String): ProcessedFile?

    /**
     * Records [files] as done, replacing any earlier record with the same [ProcessedFile.id].
     *
     * Call this only once a file has actually been handed off. Marking ahead of the handoff means
     * a failure leaves the file permanently skipped.
     */
    suspend fun markProcessed(files: Collection<ProcessedFile>)

    /**
     * Forgets what [sourceId] has done, so the next pass treats every file as new. Touches no
     * bytes on disk: this is bookkeeping, never an eviction or a delete.
     */
    suspend fun clearProcessed(sourceId: String)
}
