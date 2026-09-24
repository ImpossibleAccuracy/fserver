package com.fserver.core.sync.version

/** Hybrid logical clock rules, as pure functions over the last reading. [HybridLogicalClock] keeps the state. */
internal object Hlc {
    /** A peer further ahead than this has a wrong clock; its wall time is not adopted. */
    const val MaxDriftMs = 60_000L

    /** Reading for a local event. Never goes back, even when the wall clock does. */
    fun tick(last: HlcTimestamp, physicalMs: Long): HlcTimestamp =
        if (physicalMs > last.physicalMs) HlcTimestamp.of(physicalMs, 0)
        else next(last.physicalMs, last.logical)

    /** State after hearing [remote], so later local readings order after it. */
    fun receive(
        last: HlcTimestamp,
        remote: HlcTimestamp,
        physicalMs: Long,
        maxDriftMs: Long = MaxDriftMs,
    ): Received {
        val skewed = remote.physicalMs - physicalMs > maxDriftMs
        val remoteMs = if (skewed) physicalMs else remote.physicalMs

        val ms = maxOf(last.physicalMs, remoteMs, physicalMs)
        val timestamp = when {
            ms == last.physicalMs && ms == remoteMs -> next(ms, maxOf(last.logical, remote.logical))
            ms == last.physicalMs -> next(ms, last.logical)
            ms == remoteMs -> next(ms, remote.logical)
            else -> HlcTimestamp.of(ms, 0)
        }
        return Received(timestamp, skewed)
    }

    /** Counter spilling over carries into the millis: a reading 1 ms early beats one that repeats. */
    private fun next(physicalMs: Long, logical: Int): HlcTimestamp =
        if (logical < HlcTimestamp.MaxLogical) HlcTimestamp.of(physicalMs, logical + 1)
        else HlcTimestamp.of(physicalMs + 1, 0)

    /** @param skewed the peer's clock was more than the allowed drift ahead; worth a diagnostic. */
    data class Received(val timestamp: HlcTimestamp, val skewed: Boolean)
}
