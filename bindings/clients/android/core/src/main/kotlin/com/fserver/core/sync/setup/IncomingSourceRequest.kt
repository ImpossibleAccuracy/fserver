package com.fserver.core.sync.setup

import com.fserver.core.sync.model.SyncMode
import kotlin.time.Instant

/**
 * A peer's ask to host one of its sources on this device, parked until this device's user answers.
 */
data class IncomingSourceRequest(
    /** Chosen by peer. Both halves of a source answer to the same id. */
    val sourceId: String,
    /** Who asked. The source, once accepted, syncs with this device and no other. */
    val deviceId: String,
    /** Display name the asking side registered. */
    val label: String,
    /** The asking side's directory as a person reads it. Kept verbatim, never re-derived here. */
    val originPath: String,
    /** What the asking side runs the source under. */
    val syncMode: SyncMode,
    val receivedAt: Instant,
)
