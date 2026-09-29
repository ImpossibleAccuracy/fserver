package com.fserver.core.sync.metadata

import com.fserver.core.sync.limits.SourceUsage
import kotlin.time.Instant

/**
 * One device's half of a source, as that device last reported it - this device's own or the peer's.
 * Informational only: shown to the user, never read by any engine decision.
 */
data class PeerSourceMetadata(
    val sourceId: String,
    /** Whose half this is. */
    val deviceId: String,
    /** The device's directory as a person reads it. */
    val storagePath: String,
    /** Files the device holds for the source, and their total size. */
    val usage: SourceUsage,
    /** How much of the device's own file limits [usage] takes, in percent; null when it sets none. */
    val usedPercent: Float?,
    val updatedAt: Instant,
)
