package com.fserver.net.connection

import com.fserver.net.NetworkException
import com.fserver.net.NetworkNode
import com.fserver.net.config.NetworkConfig
import com.fserver.net.discovery.AdvertisedPeer
import com.fserver.net.discovery.DiscoveredPeer
import com.fserver.net.security.auth.AuthMethodId
import com.fserver.net.security.auth.AuthRequest
import com.fserver.net.security.crypto.PassthroughCryptoProvider
import com.fserver.net.security.identity.EphemeralIdentityStore
import com.fserver.net.session.CloseReason
import com.fserver.net.session.PeerSession
import com.fserver.net.spi.SpiId
import com.fserver.net.spi.Transport
import com.fserver.net.support.DEAD
import com.fserver.net.support.DeadEndpoint
import com.fserver.net.support.DeadTransport
import com.fserver.net.support.LOOPBACK
import com.fserver.net.support.LoopbackEndpoint
import com.fserver.net.support.LoopbackNetwork
import com.fserver.net.support.TestDictionary
import com.fserver.net.support.TestMessage
import com.fserver.net.support.TestingAuthMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ConnectionManagerTest {

    private val network = LoopbackNetwork()
    private val scope = CoroutineScope(SupervisorJob())
    private val nodes = mutableListOf<NetworkNode<TestMessage>>()

    @After
    fun tearDown() {
        nodes.forEach { it.close() }
        scope.cancel()
    }

    @Test
    fun `a route that will not open makes the manager try the next one`() = runBlocking {
        val alice = node("alice", transports = listOf(DeadTransport(), network.transport("alice")))
        val bob = node("bob")
        acceptEverything(bob)

        val session = withTimeout(TIMEOUT) {
            alice.requestsManager.connect(discovered("bob", listOf(DEAD, LOOPBACK))).getOrThrow()
        }

        assertEquals(LOOPBACK, session.route.transport)
        assertEquals(bob.identity.deviceId, session.negotiatedDeviceId)
    }

    @Test
    fun `every route failing surfaces the last failure, not a session`() = runBlocking {
        val alice = node("alice", transports = listOf(DeadTransport()))

        val outcome = withTimeout(TIMEOUT) {
            alice.requestsManager.connect(discovered("bob", listOf(DEAD)))
        }

        assertTrue(outcome.isFailure)
        assertTrue(outcome.exceptionOrNull() is NetworkException.Transport)
        assertTrue(alice.incoming.sessions.value.isEmpty())
    }

    @Test
    fun `a peer with no route at all is NoRoute, and so is one no transport carries`() =
        runBlocking {
            val alice = node("alice")

            val routeless = alice.requestsManager.connect(discovered("bob"))
            val uncarried = alice.requestsManager.connect(
                PeerRef("peer-bob", DEAD, DeadEndpoint("bob"))
            )

            assertTrue(routeless.exceptionOrNull() is NetworkException.NoRoute)
            assertTrue(uncarried.exceptionOrNull() is NetworkException.NoRoute)
        }

    @Test
    fun `the session limit is enforced once the handshake is already done`() = runBlocking {
        val alice = node(
            "alice",
            policy = POLICY.copy(sessionConfig = POLICY.sessionConfig.copy(maxSessions = 1))
        )
        val bob = node("bob")
        val carol = node("carol")
        acceptEverything(bob)
        acceptEverything(carol)

        val kept = withTimeout(TIMEOUT) { connect(alice, "bob").getOrThrow() }
        val refused = withTimeout(TIMEOUT) { connect(alice, "carol") }

        val error = refused.exceptionOrNull()
        assertTrue(error is NetworkException.Transport)
        assertTrue(error!!.message!!.contains("session limit"))
        // The one that got in first is the one that stays.
        assertEquals(listOf(kept), alice.incoming.sessions.value)
        assertTrue(kept.state.value is PeerSession.State.Ready)
    }

    @Test
    fun `probe returns the public greeting and asks nobody anything`() = runBlocking {
        val alice = node("alice")
        val bob = node("bob")

        // Deliberately no acceptEverything: a probe must not need anyone to answer for it.
        val seen = Channel<String>(Channel.UNLIMITED)
        collectIncoming(bob) { request -> seen.send(request.peer.advertisedName) }

        val greeting = withTimeout(TIMEOUT) {
            alice.requestsManager.probe(PeerRef("peer-bob", LOOPBACK, LoopbackEndpoint("bob")))
                .getOrThrow()
        }

        assertEquals(listOf(TestingAuthMethod.ID), greeting.methods)
        assertEquals(1..1, greeting.protocolVersions)
        // Nothing was created, on either side, and bob was never asked about it.
        assertTrue(alice.incoming.sessions.value.isEmpty())
        assertTrue(alice.requestsManager.profiles.value.isEmpty())
        assertTrue(bob.incoming.sessions.value.isEmpty())
        delay(SETTLE)
        assertNull(seen.tryReceive().getOrNull())
    }

    @Test
    fun `a method that was prompted for but is no longer offered ends the attempt`() = runBlocking {
        val alice = node("alice")
        val bob = node("bob")
        acceptEverything(bob)

        // The prompt came from a greeting on some other connection; this one offers nothing of
        // the kind. Running whatever is left instead would be a silent downgrade.
        val outcome = withTimeout(TIMEOUT) {
            alice.requestsManager.connect(
                PeerRef("peer-bob", LOOPBACK, LoopbackEndpoint("bob")),
                request = AuthRequest(method = AuthMethodId("spake2-imaginary")),
            )
        }

        val refused = outcome.exceptionOrNull()
        assertTrue(refused is NetworkException.Handshake)
        assertTrue(refused!!.message!!.contains("is not on offer"))
        assertTrue(alice.incoming.sessions.value.isEmpty())
    }

    @Test
    fun `an incoming request nobody accepted never becomes a session`() = runBlocking {
        val alice = node("alice")
        val bob = node("bob")

        val seen = Channel<String>(Channel.UNLIMITED)
        collectIncoming(bob) { request ->
            seen.send(request.peer.advertisedName)
            request.reject(CloseReason.RejectedByUser)
        }

        val outcome = withTimeout(TIMEOUT) { connect(alice, "bob") }

        assertEquals("alice", withTimeout(TIMEOUT) { seen.receive() })
        assertTrue(outcome.isFailure)
        assertTrue(alice.incoming.sessions.value.isEmpty())
        assertTrue(bob.incoming.sessions.value.isEmpty())
    }

    @Test
    fun `disconnect drops the session from both the registry and the peer`() = runBlocking {
        val alice = node("alice")
        val bob = node("bob")
        acceptEverything(bob)

        val session = withTimeout(TIMEOUT) { connect(alice, "bob").getOrThrow() }
        val bobSide = withTimeout(TIMEOUT) { firstSession(bob) }

        alice.requestsManager.disconnect(session.negotiatedDeviceId, CloseReason.Local("done"))

        // Unregistering trails the close on both sides, so wait for the registry rather than
        // reading it the instant the call returns.
        withTimeout(TIMEOUT) { alice.incoming.sessions.first { it.isEmpty() } }
        val end = withTimeout(TIMEOUT) { bobSide.state.first { it.isFinal } }
        assertTrue(end is PeerSession.State.Closed)
        // Reaching Closed is the session's own news; the manager unregisters just after, so wait
        // on the registry rather than reading it the instant the state flips.
        withTimeout(TIMEOUT) { bob.incoming.sessions.first { it.isEmpty() } }
        Unit
    }

    // ------------------------------------------------------------------ helpers

    private fun node(
        name: String,
        transports: List<Transport> = listOf(network.transport(name)),
        policy: ConnectionPolicy = POLICY,
    ): NetworkNode<TestMessage> = NetworkNode.create(
        NetworkConfig(
            dictionary = TestDictionary(),
            identityStore = EphemeralIdentityStore(displayName = name),
            authMethods = listOf(TestingAuthMethod()),
            transports = transports,
            policy = policy,
            scope = scope,
        )
    ).also(nodes::add)

    /** A peer as discovery would report it, with one route per transport listed. */
    private fun discovered(name: String, transports: List<SpiId> = emptyList()) = DiscoveredPeer(
        advertised = AdvertisedPeer(
            deviceId = "peer-$name",
            displayName = name,
        ),
        routes = transports.map { id ->
            val endpoint = if (id == DEAD) DeadEndpoint(name) else LoopbackEndpoint(name)
            PeerRef("peer-$name", id, endpoint)
        },
        lastSeen = Instant.now(),
    )

    private suspend fun connect(from: NetworkNode<TestMessage>, to: String) =
        from.requestsManager.connect(PeerRef("peer-$to", LOOPBACK, LoopbackEndpoint(to)))

    private suspend fun acceptEverything(node: NetworkNode<TestMessage>): Job =
        collectIncoming(node) { it.accept() }

    /** `incoming` is a hot, replay-free flow: nothing may be dialled before a collector is on it. */
    private suspend fun collectIncoming(
        node: NetworkNode<TestMessage>,
        handle: suspend (IncomingConnectionsManager.IncomingRequest) -> Unit,
    ): Job {
        val job = scope.launch { node.incoming.incoming.collect { handle(it) } }
        delay(SUBSCRIBE_GRACE)
        return job
    }

    private suspend fun firstSession(node: NetworkNode<TestMessage>): PeerSession<TestMessage> =
        node.incoming.sessions.first { it.isNotEmpty() }.first()

    private val PeerSession.State.isFinal: Boolean
        get() = this is PeerSession.State.Closed || this is PeerSession.State.Failed

    /** The id the handshake proved - not necessarily [PeerSession.route]'s, which is what was dialed. */
    private val PeerSession<*>.negotiatedDeviceId: String
        get() = (state.value as PeerSession.State.Ready).negotiated.peer.deviceId

    private companion object {
        val TIMEOUT = 10.seconds
        val SUBSCRIBE_GRACE = 50.milliseconds
        val SETTLE = 300.milliseconds

        // Deterministic: no keep-alive timers, no retries.
        val POLICY = ConnectionPolicy(timeouts = TimeoutsConfig(keepAlive = null), reconnect = null)
    }
}
