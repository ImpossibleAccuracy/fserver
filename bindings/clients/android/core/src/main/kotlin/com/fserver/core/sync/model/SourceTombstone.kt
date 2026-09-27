package com.fserver.core.sync.model

import com.fserver.core.files.SourceLocation
import kotlin.time.Instant

/** What is left of a source this device dropped, or refused before it was ever registered here. */
data class SourceTombstone(
    val sourceId: String,
    /** The device the source synced with. Only it is told the source is gone. */
    val deviceId: String,
    val removedAt: Instant,
    /**
     * Last known location of the source. The peer can use it to clean up its own records or restore
     * source. Null for a refused request: it never had one.
     */
    val location: SourceLocation?,
)
