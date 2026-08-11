package com.fserver.net.connection

import com.fserver.net.spi.TransportId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * @property transportOrder preferred route order; null keeps the order the transports were
 * registered in.
 * @property keepAlive null switches PING/PONG off. With it on, a link that stays silent for three
 * periods is treated as dead.
 */
data class ConnectionPolicy(
    val transportOrder: List<TransportId>? = null,
    val connectTimeout: Duration = 15.seconds,
    val handshakeTimeout: Duration = 10.seconds,
    val requestTimeout: Duration = 30.seconds,
    val keepAlive: Duration? = 30.seconds,
    val reconnect: ReconnectPolicy = ReconnectPolicy.ExponentialBackoff(),
    val maxSessions: Int = 16,
    val sendQueueCapacity: Int = 64,
    val incomingQueueCapacity: Int = 64,
)

/**
 * What happens after a link drops. Note what is *not* here: re-sending messages. `:net` guarantees
 * at-most-once within a session, and a blind repeat of an `evict` is how user data disappears.
 */
sealed interface ReconnectPolicy {
    data object None : ReconnectPolicy

    data class ExponentialBackoff(
        val initialDelay: Duration = 1.seconds,
        val maxDelay: Duration = 30.seconds,
        val maxAttempts: Int = 5,
    ) : ReconnectPolicy
}
