package com.fserver.files.upload

import com.fserver.common.model.ContentHash
import kotlin.time.Instant

/** One file as a single side - local or remote - currently sees it. */
data class FileRecord(
    val id: FileId,
    val path: String,
    val state: State,
    /** Content identity, or null while the index has not hashed the file yet */
    val content: ContentHash?,
    val metadata: Metadata,
) {
    /**
     * Common file info.
     *
     * For [State.Deleted] this describes the last version seen before deletion,
     * so a strategy can still reason about what was lost.
     */
    data class Metadata(
        val size: Long,
        val lastModified: Instant,
        /** Who last wrote the file and how many times, or `null` when unknown. */
        val revision: Revision?,
    )

    /**
     * What this side holds right now.
     *
     * The [Evicted] / [Deleted] split is load-bearing: eviction frees local space and must never
     * reach the other side as a user deletion. Collapsing them loses user data.
     */
    sealed interface State {
        /** Bytes are here and readable. [location] is the side-local handle (path, URI, blob key). */
        data class Present(
            val location: String,
            /** Pinned files are exempt from eviction. */
            val pinned: Boolean = false,
        ) : State

        /** Known here, bytes dropped to reclaim space. Still part of the set - not a deletion. */
        data class Evicted(val evictedAt: Instant) : State

        /** Tombstone. The user deleted it; this one does propagate. */
        data class Deleted(val deletedAt: Instant) : State
    }
}

/** Cross-device identity of a file. Equal ids on both sides mean "the same file". */
@JvmInline
value class FileId(val value: String)

/** Per-device write counter. Concurrent edits show up as two different [originDevice]s. */
data class Revision(
    /** Device that last wrote the file. */
    val originDevice: String,
    /** How many times that device wrote the file. */
    val counter: Long,
)
