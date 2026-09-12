package com.fserver.core.sync.index

import com.fserver.common.model.ContentHash
import com.fserver.common.model.FileSize
import kotlin.time.Instant

/**
 * One file a source has already worked through, as this device last saw it.
 *
 * Shaped so a pass can turn it into the record a strategy plans over without asking anything else -
 * see `toFileRecord`. The types are mirrored rather than reused because `:files` is an
 * implementation detail of `:core`, and a storage backend implementing [
 * com.fserver.core.store.sync.FileIndexStore] must compile without it on the classpath.
 */
data class LocalIndexedFile(
    /** Row key, unique within this device's index. Assigned by whoever writes the record. */
    val id: String,
    val sourceId: String,
    /** Cross-device identity: two devices holding the same file agree on this value. */
    val fileId: String,
    /** Source-relative and canonical file location. */
    val path: String,
    /** Address of the file in the local source. */
    val locator: String,
    /** What this device holds right now. */
    val state: State,
    val size: FileSize,
    /** Filesystem mtime as of processing. */
    val modifiedAt: Instant,
    val hash: ContentHash? = null,
    /** Who last wrote the file and how many times, or null when the peer does not report it. */
    val revision: Revision? = null,
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

    /**
     * What this device holds right now.
     *
     * The [Evicted] / [Deleted] split is load-bearing: eviction frees local space and must never
     * reach the other side as a user deletion. Collapsing them loses user data.
     */
    sealed interface State {
        /** Bytes are here and readable. */
        data class Present(
            /** Pinned files are exempt from eviction. */
            val pinned: Boolean = false,
        ) : State

        /** Known here, bytes dropped to reclaim space. Still part of the set - not a deletion. */
        data class Evicted(val evictedAt: Instant) : State

        /** Tombstone. The user deleted it; this one does propagate. */
        data class Deleted(val deletedAt: Instant) : State
    }

    /** Per-device write counter. Concurrent edits show up as two different [originDevice]s. */
    data class Revision(
        /** Device that last wrote the file. */
        val originDevice: String,
        /** How many times that device wrote the file. */
        val counter: Long,
    )
}
