package com.fserver.core.network.device.impl

import com.fserver.core.network.NetworkController
import com.fserver.core.network.auth.AuthMethod
import com.fserver.core.network.device.model.DeviceKind
import com.fserver.core.network.device.model.DeviceMetadata
import com.fserver.core.network.device.model.TrustedDevice
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.support.FakePeerSession
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.peerIdentity
import com.fserver.net.connection.IncomingConnectionsManager
import com.fserver.net.connection.RequestManager
import com.fserver.net.discovery.PeerDiscovery
import com.fserver.net.session.PeerSession
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * What counts as a device being online.
 *
 * A session outlives its link, so the registry holding one is not on its own evidence that
 * anything can be reached - which is the whole difference between a device that is here and one
 * that walked away.
 */
class OnlineDevicesImplTest {

    private val storage = FakeStorage()
    private val sessions = MutableStateFlow<List<PeerSession<FileServerMessages>>>(emptyList())
    private val network = mockk<NetworkController>()
    private val discovery = mockk<PeerDiscovery>()
    private val requests = mockk<RequestManager<FileServerMessages>>()

    private lateinit var devices: OnlineDevicesImpl

    @Before
    fun setUp() {
        every { network.peerDiscovery } returns discovery
        every { network.requestManager } returns requests
        every { network.incomingConnections } returns OpenSessions(sessions)
        every { discovery.peers } returns MutableStateFlow(emptyList())
        every { requests.profiles } returns MutableStateFlow(emptyMap())

        devices = OnlineDevicesImpl(network = network, storage = storage)
    }

    @Test
    fun `a session with a link in place is a connected device`() = runTest {
        val session = FakePeerSession(identity = peerIdentity(PeerId)).apply { markReady() }
        sessions.value = listOf(session)

        assertEquals(listOf(PeerId), devices.connected.first().map { it.deviceId })
    }

    @Test
    fun `a session rebuilding itself is not a connected device`() = runTest {
        val session = FakePeerSession(identity = peerIdentity(PeerId)).apply { markReady() }
        sessions.value = listOf(session)
        assertEquals(1, devices.connected.first().size)

        // The link dropped: the session stays registered while it tries to come back, and reaches
        // nobody meanwhile.
        session.markConnecting()

        assertTrue(devices.connected.first().isEmpty())
    }

    @Test
    fun `a trusted device out of sight is offline, not visible`() = runTest {
        trust(PeerId, kind = DeviceKind.Laptop)

        val offline = devices.offline.first().single()

        assertEquals(PeerId, offline.deviceId)
        assertEquals(DeviceKind.Laptop, offline.kind)
        assertTrue(devices.visible.first().isEmpty())
        assertEquals(listOf(PeerId), devices.all.first().map { it.deviceId })
    }

    @Test
    fun `a trusted device with a session is connected, not offline`() = runTest {
        trust(PeerId, kind = DeviceKind.Laptop)
        sessions.value = listOf(FakePeerSession(identity = peerIdentity(PeerId)).apply { markReady() })

        assertTrue(devices.offline.first().isEmpty())
        assertEquals(listOf(PeerId), devices.all.first().map { it.deviceId })
    }

    private suspend fun trust(deviceId: String, kind: DeviceKind?) = storage.trust.upsert(
        TrustedDevice(
            deviceId = deviceId,
            displayName = deviceId,
            publicKey = "key-of-$deviceId".toByteArray(),
            method = AuthMethod.ConfirmFingerprint,
            strength = "strong",
            metadata = DeviceMetadata(
                kind = kind,
                dictionaryId = null,
                dictionaryVersion = null,
                lastSeen = null,
                lastNetworkId = null,
            ),
        ),
    )

    private class OpenSessions(
        override val sessions: StateFlow<List<PeerSession<FileServerMessages>>>,
    ) : IncomingConnectionsManager<FileServerMessages> {
        override val incoming: Flow<IncomingConnectionsManager.IncomingRequest> = emptyFlow()

        override fun session(deviceId: String): PeerSession<FileServerMessages>? =
            sessions.value.find { it.identity.deviceId == deviceId }
    }

    private companion object {
        const val PeerId = "device-peer"
    }
}
