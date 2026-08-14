package com.fserver.net.connection.throttle

import com.fserver.net.connection.ConnectionPolicy
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Pre-authentication defenses, keyed by source address - the only thing known about a peer before
 * it proves a device id. The target protocol drops the per-connection user confirmation that used
 * to gate every incoming socket before a byte of it was parsed (protocol §7.7); this runs
 * automatically in its place, so it has to refuse on its own rather than lean on a person noticing.
 */
internal class HandshakeThrottle(
    private val policy: ConnectionPolicy,
    private val clock: TimeSource = TimeSource.Monotonic,
) {
    private val lock = Mutex()

    /** Sliding window of handshake attempts per source address. */
    private val attempts = mutableMapOf<HandshakeSource, ArrayDeque<TimeMark>>()

    /** Exponential backoff for sources that have been rejected. */
    private val failures = mutableMapOf<HandshakeSource, Failure>()

    /** Count of handshakes that have been reserved but not yet released. */
    private var pending = 0

    /**
     * True when [source] may start a new handshake right now, and reserves the slot if so - one
     * matching [release] is owed for every `true`.
     */
    suspend fun reserve(source: HandshakeSource): Boolean = lock.withLock {
        failures[source]?.let { failure ->
            // Source rejected already, and its wait has not yet expired
            if (failure.retryAt.hasNotPassedNow()) return@withLock false
        }

        // Window is full, don't start new handshake. This is a global limit, not per-source
        if (pending >= policy.throttleConfig.maxPendingHandshakes) return@withLock false

        // Count attempts in a sliding window, and reject if the source has exceeded its limit
        val window = attempts.getOrPut(source) { ArrayDeque() }
        while (window.isNotEmpty() &&
            window.first().elapsedNow() > policy.throttleConfig.window
        ) {
            window.removeFirst()
        }
        if (window.size >= policy.throttleConfig.maxAttempts) return@withLock false

        window.addLast(clock.markNow())
        pending++
        true
    }

    suspend fun release(): Unit = lock.withLock {
        pending--
    }

    /** A rejected authentication - not a probe, not a dropped link - earns [source] a growing wait. */
    suspend fun onAuthenticationFailed(source: HandshakeSource): Unit = lock.withLock {
        val delay = failures[source]
            ?.let { (it.delay * 2).coerceAtMost(policy.throttleConfig.maxDelay) }
            ?: policy.throttleConfig.initialDelay

        failures[source] = Failure(
            delay = delay,
            retryAt = clock.markNow() + delay
        )
    }

    /** A source that finally authenticated, so its backoff is cleared. */
    suspend fun onAuthenticated(source: HandshakeSource): Unit = lock.withLock {
        failures.remove(source)
    }

    private class Failure(val delay: Duration, val retryAt: TimeMark)
}
