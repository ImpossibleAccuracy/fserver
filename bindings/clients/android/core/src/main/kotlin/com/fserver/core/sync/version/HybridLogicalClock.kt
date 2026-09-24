package com.fserver.core.sync.version

import com.fserver.core.store.FServerStorage
import com.fserver.core.util.TimeProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * This device's HLC. The last reading is persisted, so readings keep growing across restarts even if
 * the wall clock went back meanwhile.
 */
internal class HybridLogicalClock(
    private val storage: FServerStorage,
    private val timeProvider: TimeProvider,
) {
    private val mutex = Mutex()
    private var last: HlcTimestamp? = null

    suspend fun now(): HlcTimestamp = ticks(1).single()

    /** [count] increasing readings for one batch of local events, persisted once. */
    suspend fun ticks(count: Int): List<HlcTimestamp> = if (count == 0) emptyList() else mutex.withLock {
        var current = loaded()
        val readings = List(count) {
            current = Hlc.tick(current, physicalMs())
            current
        }
        save(current)
        readings
    }

    /** Folds a peer's reading in, so what this device does next orders after what it has seen. */
    suspend fun receive(remote: HlcTimestamp) = mutex.withLock {
        val received = Hlc.receive(loaded(), remote, physicalMs())

        if (received.skewed) {
            // TODO: surface in diagnostics once there is a place for clock warnings.
            Timber.w("Peer clock is ahead by more than ${Hlc.MaxDriftMs} ms: $remote")
        }

        save(received.timestamp)
    }

    private suspend fun loaded(): HlcTimestamp =
        last ?: (storage.preferences.loadClock() ?: HlcTimestamp.Zero).also { last = it }

    private suspend fun save(timestamp: HlcTimestamp) {
        storage.preferences.saveClock(timestamp)
        last = timestamp
    }

    private fun physicalMs(): Long = timeProvider.now().toEpochMilliseconds()
}
