package com.fserver.net.connection

import com.fserver.net.spi.SpiId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * @property transportOrder preferred route order; null keeps the order the transports were registered in.
 */
data class ConnectionPolicy(
    val transportOrder: List<SpiId>? = null,
    val authConfig: AuthConfig = AuthConfig(),
    val timeouts: TimeoutsConfig = TimeoutsConfig(),
    val reconnect: ReconnectPolicy? = ReconnectPolicy.ExponentialBackoff(),
    val sessionConfig: SessionConfig = SessionConfig(),
    val throttleConfig: ThrottleConfig = ThrottleConfig(),
    val advertisement: AdvertisementPolicy = AdvertisementPolicy(),
)

data class AuthConfig(
    /**
     * cap on the exchanges one authentication method may run,
     * so a peer cannot keep a pre-authentication connection alive forever by never finishing.
     */
    val maxAuthRounds: Int = 8,
    /**
     * how long to wait for one authentication round.
     * Should be longer than [TimeoutsConfig.handshake]: person comparing digits or typing
     * password is not a stalled network, and holding both to the same deadline
     * drops connections the moment someone hesitates.
     */
    val authTimeout: Duration = 2.minutes,
)

data class TimeoutsConfig(
    val connect: Duration = 15.seconds,
    val handshake: Duration = 10.seconds,
    val request: Duration = 30.seconds,
    /** null switches PING/PONG off. With it on, a link that stays silent for three periods is treated as dead. */
    val keepAlive: Duration? = 30.seconds,
)

data class SessionConfig(
    val maxSessions: Int = 16,
    val sendQueueCapacity: Int = 64,
    val incomingQueueCapacity: Int = 64,
)

data class ThrottleConfig(
    /** how far back [maxAttempts] looks; a fixed sliding window per source address. */
    val window: Duration = 10.seconds,
    val maxAttempts: Int = 5,
    /** cap on connections running the public greeting or `AUTH` at once, unauthenticated */
    val maxPendingHandshakes: Int = 32,
    /** the wait after the first rejection; each further one doubles it, capped at [maxDelay]. */
    val initialDelay: Duration = 30.seconds,
    val maxDelay: Duration = 5.minutes,
)

/** What happens after a link drops. */
sealed interface ReconnectPolicy {
    val maxAttempts: Int

    data class StaticDelay(
        val delay: Duration = 5.seconds,
        override val maxAttempts: Int = 5,
    ) : ReconnectPolicy

    data class ExponentialBackoff(
        val initialDelay: Duration = 1.seconds,
        val maxDelay: Duration = 30.seconds,
        override val maxAttempts: Int = 5,
    ) : ReconnectPolicy
}

/**
 * What this device puts on the air about itself.
 *
 * @property enabled false stops the device announcing itself at all.
 * @property publishName false trades the device list showing a name for saying even less.
 */
data class AdvertisementPolicy(
    val enabled: Boolean = true,
    val publishName: Boolean = true,
)
