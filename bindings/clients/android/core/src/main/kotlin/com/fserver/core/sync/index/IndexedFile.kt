package com.fserver.core.sync.index

import com.fserver.common.model.ContentHash
import com.fserver.common.model.FileSize
import kotlin.time.Instant

/**
 * One file a source has already worked through, and enough about it to tell whether it has changed since.
 */
data class IndexedFile(
    val id: String,
    val sourceId: String,
    /** Where the file was when it was processed. */
    val path: String,
    val size: FileSize,
    /** Filesystem mtime as of processing. */
    val modifiedAt: Instant,
    val hash: ContentHash? = null,
    val processedAt: Instant,
) {
    /**
     * Whether a freshly scanned version of this file looks untouched, so a pass can skip it.
     *
     * Hash wins when both sides have one. Otherwise, this falls back to size + mtime, which misses
     * an edit that preserved both - rare, but the reason [hash] exists.
     */
    fun matches(size: FileSize, modifiedAt: Instant, hash: ContentHash? = null): Boolean =
        if (hash != null && this.hash != null) {
            this.hash == hash
        } else {
            this.size == size && this.modifiedAt == modifiedAt
        }
}
