package com.fserver.net.transport.android.spi.multicastdns

import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.connection.LanPorts
import com.fserver.net.spi.Transport
import com.fserver.net.transport.android.datasource.multicastdns.MulticastDnsPortBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket

/** Real loopback sockets: the point of a fixed port list is what the kernel does with it. */
class MulticastDnsPortsTest {

    @Test
    fun `listener takes a preferred port`() = runBlocking {
        val first = startedTransport()

        try {
            assertTrue(
                "listener bound ${first.port}, none of ${LanPorts.PREFERRED}",
                first.port in LanPorts.PREFERRED,
            )
        } finally {
            first.stop()
        }
    }

    @Test
    fun `second listener moves down the list instead of failing`() = runBlocking {
        val first = startedTransport()
        val second = startedTransport()

        try {
            assertTrue(second.port in LanPorts.PREFERRED)
            assertNotEquals(first.port, second.port)
        } finally {
            first.stop()
            second.stop()
        }
    }

    @Test
    fun `a dial on a stale port sweeps the rest of the list`() = runBlocking {
        val listening = startedTransport()
        val dialler = MulticastDnsTransport(MulticastDnsPortBinder(), ConnectionPolicy())

        try {
            val channel = dialler.open(
                MulticastDnsTransportEndpoint(host = LOOPBACK, port = deadPort())
            ).getOrThrow()

            // The endpoint carries the port that answered, so the route recorded is dialable.
            assertEquals(
                "$LOOPBACK:${listening.port}",
                channel.endpoint.address,
            )
            channel.close()
        } finally {
            listening.stop()
        }
    }

    @Test
    fun `an inbound route is dialled by guessing over its host`() = runBlocking {
        val listening = startedTransport()
        val dialler = MulticastDnsTransport(MulticastDnsPortBinder(), ConnectionPolicy())

        // Port of an accepted socket is the peer's source port - useless, so only the host is.
        val inbound = MulticastDnsTransportEndpoint(
            host = LOOPBACK,
            port = deadPort(),
            isDialable = false,
        )

        try {
            assertTrue(dialler.supports(inbound))

            val channel = dialler.open(inbound).getOrThrow()
            assertEquals("$LOOPBACK:${listening.port}", channel.endpoint.address)
            channel.close()
        } finally {
            listening.stop()
        }
    }

    private class Started(
        val transport: MulticastDnsTransport,
        val port: Int,
        private val job: Job,
    ) {
        suspend fun stop() {
            job.cancel()
            transport.shutdown()
        }
    }

    private suspend fun CoroutineScope.startedTransport(): Started {
        val binder = MulticastDnsPortBinder()
        val transport = MulticastDnsTransport(binder, ConnectionPolicy())

        val job = launch(Dispatchers.IO) {
            transport.listener.listen().collect(::reject)
        }

        return Started(transport, binder.value.filterNotNull().first(), job)
    }

    private suspend fun reject(connection: Transport.InboundConnection) = connection.reject()

    /** A port nothing listens on: bound, read back, released. */
    private fun deadPort(): Int = ServerSocket(0).use { it.localPort }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
    }
}
