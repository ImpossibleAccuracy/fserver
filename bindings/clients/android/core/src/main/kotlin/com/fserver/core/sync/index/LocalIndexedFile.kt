package com.fserver.core.sync.index

import com.fserver.common.model.ContentHash
import com.fserver.common.model.FileSize
import com.fserver.core.sync.version.HlcTimestamp
import com.fserver.core.sync.version.VersionVector
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
    /** Where this version sits in the file's history, or null when the peer does not report it. */
    val version: Version? = null,
    /**
     * The bytes were touched since [hash] was taken, so it may no longer describe them. Kept rather
     * than dropped: the next hash is compared with it to tell an edit from a touch.
     */
    val hashStale: Boolean = false,
    val processedAt: Instant,
) {
    val isDeleted: Boolean
        get() = state is State.Deleted

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
            /** When the bytes were fetched on demand after an eviction, or null. Evicted again after a TTL. */
            val fetchedAt: Instant? = null,
        ) : State

        /** Known here, bytes dropped to reclaim space. Still part of the set - not a deletion. */
        data class Evicted(val evictedAt: Instant) : State

        /** Tombstone. The user deleted it; this one does propagate. */
        data class Deleted(val deletedAt: Instant) : State
    }

    /** One version of the file. Only [vector] orders versions; the rest breaks ties between concurrent ones. */
    data class Version(
        val vector: VersionVector,
        /** When the version was made. Never used to tell older from newer - see [vector]. */
        val hlc: HlcTimestamp,
        /** Device that made the version. */
        val originDevice: String,
    )
}
