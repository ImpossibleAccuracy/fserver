package com.fserver.core.sync.clock

import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import com.fserver.core.sync.version.Hlc
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import timber.log.Timber
import kotlin.math.abs

/**
 * Wall-clock offset to each peer, measured before every pass. Kept in memory only: a pass measures
 * again before it resolves anything.
 */
internal class ClockSkews {
    /** deviceId to the peer's clock minus ours, in ms. */
    private val offsets = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** Peers whose clock is off by more than [Hlc.MaxDriftMs], either way. */
    val skewed: Flow<Set<String>> = offsets
        .map { all -> all.filterValues(::isSkewed).keys }
        .distinctUntilChanged()

    /** Returns whether the peer's clock is off by more than [Hlc.MaxDriftMs]. */
    fun record(deviceId: String, offsetMs: Long): Boolean {
        offsets.update { it + (deviceId to offsetMs) }

        val skewed = isSkewed(offsetMs)
        if (skewed) Timber.w("Clock of $deviceId is off by $offsetMs ms from ours")
        return skewed
    }

    fun resolution(source: SourceEntry): SyncMode.Mirror.ConflictResolution? =
        source.conflictResolution(peerSkewed = offsets.value[source.deviceId]?.let(::isSkewed) == true)

    private fun isSkewed(offsetMs: Long): Boolean = abs(offsetMs) > Hlc.MaxDriftMs
}

/** The source's resolution, held to Ask while the peer's clock is off: LWW would pick by a wrong clock. */
internal fun SourceEntry.conflictResolution(peerSkewed: Boolean): SyncMode.Mirror.ConflictResolution? {
    val configured = (syncMode as? SyncMode.Mirror)?.conflictResolution ?: return null
    return if (peerSkewed) SyncMode.Mirror.ConflictResolution.Ask else configured
}
