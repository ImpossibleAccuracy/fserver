package com.fserver.core.files.source

import com.fserver.core.files.model.FileSize
import kotlin.time.Instant

/**
 * One file a source has already worked through, and enough about it to tell whether it has changed
 * since.
 *
 * Identity is (sourceId, path), folded into [id] by [idOf] - the same file recorded twice replaces
 * its own record instead of piling up a second one. The same path under two sources stays two
 * records, because each source tracks its own progress.
 *
 * Constructor is public: a storage backend rebuilds these from its own columns.
 */
data class ProcessedFile(
    val id: String,
    val sourceId: String,
    /** Where the file was when it was processed - a filesystem path or a SAF document uri. */
    val path: String,
    val size: FileSize,
    /** Filesystem mtime as of processing. Cheap half of the change check. */
    val modifiedAt: Instant,
    /**
     * Content digest. Null when the file was processed without hashing, which leaves [size] and
     * [modifiedAt] as the only evidence it has not changed.
     */
    val hash: FileHash? = null,
    val processedAt: Instant,
) {
    /**
     * Whether a freshly scanned version of this file looks untouched, so a pass can skip it.
     *
     * Hash wins when both sides have one. Otherwise this falls back to size + mtime, which misses
     * an edit that preserved both - rare, but the reason [hash] exists.
     */
    fun matches(size: FileSize, modifiedAt: Instant, hash: FileHash? = null): Boolean =
        if (hash != null && this.hash != null) {
            this.hash == hash
        } else {
            this.size == size && this.modifiedAt == modifiedAt
        }

    companion object {
        /** Stable per (sourceId, path), so re-processing a file replaces rather than duplicates. */
        fun idOf(sourceId: String, path: String): String = "$sourceId/$path"
    }
}
