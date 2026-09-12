package com.fserver.core.network.device.impl

import com.fserver.common.exception.NetworkException
import com.fserver.core.network.DeviceUnreachableException
import com.fserver.core.network.NetworkController
import com.fserver.core.network.PeerIdentityMismatchException
import com.fserver.core.network.TransportKind
import com.fserver.core.network.auth.impl.InteractivePeerAuthenticator
import com.fserver.core.network.device.model.KnownRoute
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.info.NetworkInfoRepository
import com.fserver.core.network.info.model.NetworkInfo
import com.fserver.core.network.info.model.PeerLocator
import com.fserver.core.requirement.RequirementsChecker
import com.fserver.core.support.FakePeerSession
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.peerIdentity
import com.fserver.net.connection.IncomingConnectionsManager
import com.fserver.net.connection.PeerRef
import com.fserver.net.connection.ProbeResult
import com.fserver.net.connection.RequestManager
import com.fserver.net.discovery.AdvertisedPeer
import com.fserver.net.discovery.DiscoveredPeer
import com.fserver.net.discovery.PeerDiscovery
import com.fserver.net.peer.PublicGreeting
import com.fserver.net.session.PeerSession
import com.fserver.net.transport.android.spi.ip.DirectIpEndpoint
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Dialing a device, and what is written down about how it was reached.
 *
 * Everything that names a device before the handshake - an advertisement, a stored route, a typed
 * address - is a claim. This class is where those claims are turned into a session, so it is where
 * a claim that turns out to be false has to cost nothing.
 *
 * `NetworkController` is mocked: final, and its constructor raises a live `NetworkNode`. The
 * slices behind it (`RequestManager`, `PeerDiscovery`, `IncomingConnectionsManager`) are
 * interfaces.
 */
class DevicesRepositoryImplTest {

    private val storage = FakeStorage()
    private val requests = mockk<RequestManager<FileServerMessages>>()
    private val discovery = mockk<PeerDiscovery>()
    private val network = mockk<NetworkController>()

    private lateinit var repository: DevicesRepositoryImpl

    @Before
    fun setUp() {
        every { network.requestManager } returns requests
        every { network.peerDiscovery } returns discovery
        every { network.incomingConnections } returns NoOpenSessions()
        every { requests.profile(any()) } returns null
        every { discovery.peers } returns MutableStateFlow(emptyList())

        repository = DevicesRepositoryImpl(
            network = network,
            requirementsChecker = mockk<RequirementsChecker>(relaxed = true),
            networkInfoRepository = OneNetwork(),
            jsonQrCodeParser = JsonQrCodeParser(),
            interactiveAuthenticator = InteractivePeerAuthenticator(),
            storage = storage,
        )
    }

    @Test
    fun `a route is recorded under the identity the handshake proved`() = runTest {
        val session = session(deviceId = RealId, host = "10.0.0.5", port = 8384)
        coEvery { requests.connect(any<PeerRef>(), any(), any()) } returns Result.success(session)

        val result = repository.connect(PeerLocator.Ip("10.0.0.5", 8384), null)

        assertTrue(result.isSuccess)
        val recorded = storage.trust.routes[RealId] as KnownRoute.Ip
        assertEquals("10.0.0.5", recorded.host)
        // A bare address names no device until the handshake does, so nothing may be filed under
        // what was dialled.
        assertNull(storage.trust.routes[DialledId])
    }

    @Test
    fun `a session that answers under a different identity is closed, not used`() = runTest {
        val session = session(deviceId = RealId, host = "10.0.0.5", port = 8384)
        every { discovery.peers } returns MutableStateFlow(listOf(discovered(DialledId)))
        coEvery {
            requests.connect(any<DiscoveredPeer>(), any(), any())
        } returns Result.success(session)

        val result = repository.connect(PeerLocator.DiscoveredDevice(DialledId), null)

        assertTrue(result.exceptionOrNull() is PeerIdentityMismatchException)
        assertNotNull(session.closedWith)
        assertTrue(storage.trust.routes.isEmpty())
    }

    @Test
    fun `a probe records no route`() = runTest {
        coEvery { requests.probe(any<PeerRef>(), any()) } returns Result.success(probeResult())

        val result = repository.probe(PeerLocator.Ip("10.0.0.5", 8384))

        assertTrue(result.isSuccess)
        // A route recorded from a probe would let anyone announcing a trusted device's id replace
        // that device's stored address - the probe proved nothing about who answered.
        assertTrue(storage.trust.routes.isEmpty())
    }

    @Test
    fun `a peer that answered and refused is not dialled again over another route`() = runTest {
        storage.trust.recordKnownRoute(DialledId, storedRoute(), networkId = null)
        every { discovery.peers } returns MutableStateFlow(listOf(discovered(DialledId)))
        coEvery { requests.connect(any<DiscoveredPeer>(), any(), any()) } returns
                Result.failure(NetworkException.Handshake("rejected by user"))

        val result = repository.connect(PeerLocator.KnownDevice(DialledId), null)

        assertTrue(result.isFailure)
        // Asking again over the stored route would only make the peer refuse twice, and prompt
        // its user twice.
        coVerify(exactly = 0) { requests.connect(any<PeerRef>(), any(), any()) }
    }

    @Test
    fun `a peer that answered under the wrong identity is still tried on another route`() =
        runTest {
            storage.trust.recordKnownRoute(DialledId, storedRoute(), networkId = null)
            every { discovery.peers } returns MutableStateFlow(listOf(discovered(DialledId)))

            // Someone else holds the discovered route; the device itself is still on the stored one.
            coEvery { requests.connect(any<DiscoveredPeer>(), any(), any()) } returns
                    Result.success(session(deviceId = RealId, host = "10.0.0.5", port = 8384))
            coEvery { requests.connect(any<PeerRef>(), any(), any()) } returns
                    Result.success(session(deviceId = DialledId, host = "10.0.0.9", port = 8384))

            val result = repository.connect(PeerLocator.KnownDevice(DialledId), null)

            assertTrue(result.isSuccess)
            assertEquals("10.0.0.9", (storage.trust.routes[DialledId] as KnownRoute.Ip).host)
        }

    @Test
    fun `a device with nothing left to dial fails as unreachable`() = runTest {
        every { discovery.peers } returns MutableStateFlow(emptyList())

        val result = repository.connect(PeerLocator.KnownDevice(DialledId), null)

        assertTrue(result.exceptionOrNull() is DeviceUnreachableException)
    }

    @Test
    fun `a malformed connection code is refused before anything is dialled`() = runTest {
        val result = repository.connect(PeerLocator.QrPayload("not json at all"), null)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { requests.connect(any<PeerRef>(), any(), any()) }
    }

    private fun session(deviceId: String, host: String, port: Int) = FakePeerSession(
        identity = peerIdentity(deviceId),
        route = PeerRef.build(DirectIpEndpoint(host = host, port = port)),
    )

    private fun discovered(deviceId: String) = DiscoveredPeer(
        advertised = AdvertisedPeer(deviceId = deviceId, displayName = "Peer"),
        routes = listOf(PeerRef.build(DirectIpEndpoint(host = "10.0.0.5", port = 8384))),
        lastSeen = java.time.Instant.EPOCH,
    )

    private fun storedRoute() = KnownRoute.Ip(
        transport = TransportKind.ManualAddress,
        host = "10.0.0.9",
        port = 8384,
        isDialable = true,
    )

    private fun probeResult() = ProbeResult(
        route = PeerRef.build(DirectIpEndpoint(host = "10.0.0.5", port = 8384)),
        greeting = PublicGreeting(protocolVersions = 1..1, methods = emptyList()),
    )

    private class NoOpenSessions : IncomingConnectionsManager<FileServerMessages> {
        override val sessions: StateFlow<List<PeerSession<FileServerMessages>>> =
            MutableStateFlow(emptyList())
        override val incoming: Flow<IncomingConnectionsManager.IncomingRequest> = emptyFlow()
        override fun session(deviceId: String): PeerSession<FileServerMessages>? = null
    }

    private class OneNetwork : NetworkInfoRepository {
        override val networkInfo: Flow<NetworkInfo?> = flowOf(NetworkInfo.Wired)
        override fun refresh() = Unit
    }

    private companion object {
        /** What the caller asked for. */
        const val DialledId = "device-asked-for"

        /** What the handshake proved. */
        const val RealId = "device-that-answered"
    }
}
