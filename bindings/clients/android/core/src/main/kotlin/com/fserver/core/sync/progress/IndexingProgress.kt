package com.fserver.core.sync.progress

import kotlin.time.Instant

/**
 * The newest indexing run over one source: the scan that brings the index in line with the disk,
 * then hashing of new files that may be renames. Separate from a sync pass - a source is indexed
 * without one too, on a peer's request or by hand.
 */
data class IndexingProgress(
    val sourceId: String,
    val stage: Stage,
    val startedAt: Instant,
    val updatedAt: Instant,
    val filesScanned: Int = 0,
    val bytesScanned: Long = 0,
    /** Possible renames hashed so far, of [filesToHash], during [Stage.Hashing]. */
    val filesHashed: Int = 0,
    val filesToHash: Int = 0,
) {
    val isFinished: Boolean
        get() = stage == Stage.Finished || stage == Stage.Failed

    enum class Stage {
        Scanning,

        /** Hashing new files that may be renames, so the peer renames its copy instead of receiving one. */
        Hashing,

        Finished,

        Failed,
    }
}
