package com.fserver.core.lifecycle.network

import com.fserver.core.network.TransportKind
import com.fserver.core.network.auth.AuthMethod
import com.fserver.core.network.device.model.KnownRoute
import com.fserver.core.network.device.model.TrustedDevice
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.sourceEntry
import com.fserver.core.sync.model.SourceEntry
import com.fserver.net.connection.IncomingConnectionsManager
import com.fserver.net.session.CloseReason
import com.fserver.net.spi.DiscoveredEndpoint
import com.fserver.net.spi.SpiId
import com.fserver.net.spi.TransportEndpoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Who gets answered without the user being asked. The id a request carries is a guess either way,
 * so what is asserted here is only which requests are taken off the host's hands.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoAcceptCoordinatorTest {

    private val storage = FakeStorage()

    @Test
    fun `a trusted device with an active source is answered, and never reaches the host`() =
        runTest {
            val coordinator = coordinator()
            coordinator.start()
            paired(Peer)

            val request = request(host = PeerHost)
            val handled = coordinator.handles(request)
            advanceUntilIdle()

            assertTrue(handled)
            assertEquals(Settled.Accepted, request.settled)
        }

    @Test
    fun `an untrusted device on the same address is left to the user`() = runTest {
        val coordinator = coordinator()
        coordinator.start()
        // Source registered against it, but nothing ever authenticated: not a pair.
        storage.sources.upsert(sourceEntry(id = "source-1", deviceId = Peer))
        storage.trust.recordKnownRoute(Peer, ipRoute(PeerHost), networkId = null)

        val request = request(host = PeerHost)
        val handled = coordinator.handles(request)
        advanceUntilIdle()

        assertFalse(handled)
        assertEquals(Settled.Pending, request.settled)
    }

    @Test
    fun `a trusted device with no source of ours is left to the user`() = runTest {
        val coordinator = coordinator()
        coordinator.start()
        trust(Peer)
        storage.trust.recordKnownRoute(Peer, ipRoute(PeerHost), networkId = null)

        val request = request(host = PeerHost)

        assertFalse(coordinator.handles(request))
    }

    @Test
    fun `a source that was disabled stops the device being answered`() = runTest {
        val coordinator = coordinator()
        coordinator.start()
        paired(Peer)
        storage.sources.updateStatus("source-1", SourceEntry.Status.Disabled("test"))

        assertFalse(coordinator.handles(request(host = PeerHost)))
    }

    @Test
    fun `nothing is answered until the host asks for it, and not after it stops`() = runTest {
        val coordinator = coordinator()
        paired(Peer)

        assertFalse(coordinator.handles(request(host = PeerHost)))

        coordinator.start()
        assertTrue(coordinator.handles(request(host = PeerHost)))

        coordinator.stop()
        assertFalse(coordinator.handles(request(host = PeerHost)))
    }

    // ------------------------------------------------------------------ helpers

    private fun TestScope.coordinator() = AutoAcceptCoordinator(
        storage = storage,
        backgroundScope = this,
    )

    /** Trusted, and with an active source against it - what an answered device looks like. */
    private suspend fun paired(deviceId: String) {
        trust(deviceId)
        storage.sources.upsert(sourceEntry(id = "source-1", deviceId = deviceId))
        storage.trust.recordKnownRoute(deviceId, ipRoute(PeerHost), networkId = null)
    }

    private suspend fun trust(deviceId: String) = storage.trust.upsert(
        TrustedDevice(
            deviceId = deviceId,
            displayName = "Peer",
            publicKey = "key-of-$deviceId".toByteArray(),
            method = AuthMethod.ConfirmFingerprint,
            strength = "strong",
        )
    )

    private fun ipRoute(host: String) = KnownRoute.Ip(
        transport = TransportKind.MulticastDns,
        host = host,
        port = 7777,
        isDialable = true,
    )

    private fun request(host: String, attributes: Map<String, String> = emptyMap()) =
        FakeIncomingRequest(host = host, attributes = attributes)

    private enum class Settled { Pending, Accepted, Rejected }

    /** An inbound endpoint: the port is the peer's ephemeral one, so it is not dialable. */
    private class InboundEndpoint(host: String) : TransportEndpoint {
        override val transport: SpiId = SpiId("multicast-dns")
        override val address: String = "$host:54321/inbound"
        override val hostAddress: String = host
        override val isDialable: Boolean = false
    }

    private class FakeIncomingRequest(
        host: String,
        attributes: Map<String, String>,
    ) : IncomingConnectionsManager.IncomingRequest {
        var settled: Settled = Settled.Pending
            private set

        override val transport: SpiId = SpiId("multicast-dns")
        override val peer: DiscoveredEndpoint = DiscoveredEndpoint(
            endpoint = InboundEndpoint(host),
            advertisedName = host,
            attributes = attributes,
        )
        override val confirmationCode: String? = null

        override suspend fun accept(): Result<Unit> {
            settled = Settled.Accepted
            return Result.success(Unit)
        }

        override suspend fun reject(reason: CloseReason) {
            settled = Settled.Rejected
        }
    }

    private companion object {
        const val Peer = "device-peer"
        const val PeerHost = "192.168.1.7"
    }
}
