package com.fserver.core.sync.model

import kotlin.time.Instant

/**
 * A file a peer is pushing here, parked in staging until it is whole. Survives the session and the
 * process, so the peer resumes from [committedOffset] instead of starting over.
 */
data class StagedUpload(
    val sourceId: String,
    val fileId: String,
    /** The peer pushing it. */
    val deviceId: String,
    /** The staging file. */
    val locator: String,
    /** Size, mtime and version of what the peer said it sends: another version starts over. */
    val size: Long,
    val modifiedAt: Instant,
    val versionHlc: Long?,
    val versionOrigin: String?,
    /** `[0, committedOffset)` is on disk and flushed. */
    val committedOffset: Long,
    val startedAt: Instant,
    /** Last checkpoint. What garbage collection ages it by. */
    val touchedAt: Instant,
)
