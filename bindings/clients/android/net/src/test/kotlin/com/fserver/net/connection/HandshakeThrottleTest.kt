package com.fserver.net.connection

import com.fserver.net.connection.throttle.HandshakeSource
import com.fserver.net.connection.throttle.HandshakeThrottle
import com.fserver.net.spi.SpiId
import com.fserver.net.spi.TransportEndpoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** Pre-authentication defenses, driven directly rather than through a whole handshake. */
class HandshakeThrottleTest {

    /** Same transport, same textual address - a fresh instance each time, as a real connection gets. */
    private fun source(name: String) = HandshakeSource(TEST_TRANSPORT, FakeEndpoint(name))

    private data class FakeEndpoint(override val address: String) : TransportEndpoint {
        override val transport: SpiId = TEST_TRANSPORT
    }

    private companion object {
        val TEST_TRANSPORT = SpiId("test")
    }

    @Test
    fun `a rejected authentication outlives the connection it happened on`() = runBlocking {
        val clock = TestTimeSource()
        val throttle = HandshakeThrottle(
            policy = ConnectionPolicy(
                throttleConfig = ThrottleConfig(
                    maxAttempts = 100,
                    initialDelay = 5.seconds,
                    maxDelay = 5.seconds
                ),
            ),
            clock = clock,
        )

        // The connection this failed on is already gone by the time the backoff matters.
        Assert.assertTrue(throttle.reserve(source("alice")))
        throttle.release()
        throttle.onAuthenticationFailed(source("alice"))

        // A brand-new connection from the same source is refused before it costs anything.
        Assert.assertFalse(throttle.reserve(source("alice")))

        clock += 5.seconds
        Assert.assertTrue(throttle.reserve(source("alice")))
    }

    @Test
    fun `each further rejection doubles the wait, up to the cap`() = runBlocking {
        val clock = TestTimeSource()
        val throttle = HandshakeThrottle(
            policy = ConnectionPolicy(
                throttleConfig = ThrottleConfig(
                    maxAttempts = 100,
                    initialDelay = 1.seconds,
                    maxDelay = 3.seconds
                ),
            ),
            clock = clock,
        )

        throttle.onAuthenticationFailed(source("alice")) // 1s
        clock += 999.milliseconds
        Assert.assertFalse(throttle.reserve(source("alice")))
        clock += 1.milliseconds
        Assert.assertTrue(throttle.reserve(source("alice")))
        throttle.release()

        throttle.onAuthenticationFailed(source("alice")) // 2s
        clock += 1999.milliseconds
        Assert.assertFalse(throttle.reserve(source("alice")))
        clock += 1.milliseconds
        Assert.assertTrue(throttle.reserve(source("alice")))
        throttle.release()

        throttle.onAuthenticationFailed(source("alice")) // would be 4s uncapped, capped at 3s
        clock += 3.seconds
        Assert.assertTrue(throttle.reserve(source("alice")))
    }

    @Test
    fun `a successful authentication clears what came before it`() = runBlocking {
        val throttle = HandshakeThrottle(
            policy = ConnectionPolicy(
                throttleConfig = ThrottleConfig(
                    initialDelay = 10.seconds,
                    maxDelay = 10.seconds
                ),
            ),
        )

        throttle.onAuthenticationFailed(source("alice"))
        throttle.onAuthenticated(source("alice"))

        Assert.assertTrue(throttle.reserve(source("alice")))
    }

    @Test
    fun `concurrent unauthenticated handshakes are capped regardless of source`() = runBlocking {
        val throttle = HandshakeThrottle(
            policy = ConnectionPolicy(
                throttleConfig = ThrottleConfig(maxPendingHandshakes = 2)
            )
        )

        Assert.assertTrue(throttle.reserve(source("alice")))
        Assert.assertTrue(throttle.reserve(source("bob")))
        Assert.assertFalse(throttle.reserve(source("carol")))

        throttle.release()
        Assert.assertTrue(throttle.reserve(source("carol")))
    }

    @Test
    fun `a source opening handshakes too fast is refused within the window`() = runBlocking {
        val clock = TestTimeSource()
        val throttle = HandshakeThrottle(
            policy = ConnectionPolicy(
                throttleConfig = ThrottleConfig(
                    window = 10.seconds,
                    maxAttempts = 2
                )
            ),
            clock = clock,
        )

        Assert.assertTrue(throttle.reserve(source("alice")))
        throttle.release()
        Assert.assertTrue(throttle.reserve(source("alice")))
        throttle.release()
        Assert.assertFalse(throttle.reserve(source("alice")))

        // Another source is unaffected: the window is per source address.
        Assert.assertTrue(throttle.reserve(source("bob")))

        clock += 10.seconds + 1.milliseconds
        Assert.assertTrue(throttle.reserve(source("alice")))
    }
}