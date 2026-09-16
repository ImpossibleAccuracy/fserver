package com.fserver.net.transport.android.spi.ip

import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.connection.LanPorts
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import kotlin.time.Duration.Companion.milliseconds

/**
 * Every route written down - typed, scanned, or remembered - is re-dialled through this transport,
 * so the fixed port list has to be swept here as well.
 */
class DirectIpPortSweepTest {

    @Test
    fun `a stale port falls through to the fixed list`() = runBlocking {
        val transport = DirectIpTransport(ConnectionPolicy())
        val listener = ServerSocket(LanPorts.DEFAULT)

        try {
            val channel = transport.open(
                DirectIpEndpoint(host = LOOPBACK, port = deadPort())
            ).getOrThrow()

            assertEquals("$LOOPBACK:${LanPorts.DEFAULT}", channel.endpoint.address)
            channel.close()
        } finally {
            listener.close()
        }
    }

    @Test
    fun `an unreachable host is not swept`() = runBlocking {
        val policy = ConnectionPolicy(
            timeouts = ConnectionPolicy().timeouts.copy(connect = 200.milliseconds)
        )
        val transport = DirectIpTransport(policy)

        // Reserved for documentation (RFC 5737), so the connect can only time out.
        val started = System.nanoTime()
        val result = transport.open(DirectIpEndpoint(host = "192.0.2.1", port = LanPorts.DEFAULT))
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000

        assertTrue(result.isFailure)
        // One timeout, not one per port: a host that never answers is not there at all.
        assertTrue("swept the list anyway, took ${elapsedMillis}ms", elapsedMillis < 600)
    }

    private fun deadPort(): Int = ServerSocket(0).use { it.localPort }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
    }
}
