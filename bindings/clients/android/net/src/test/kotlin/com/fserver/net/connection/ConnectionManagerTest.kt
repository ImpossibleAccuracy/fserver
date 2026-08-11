package com.fserver.net.connection

import com.fserver.net.NetworkConfig
import com.fserver.net.NetworkException
import com.fserver.net.NetworkNode
import com.fserver.net.discovery.DiscoveredPeer
import com.fserver.net.security.EphemeralIdentityStore
import com.fserver.net.session.CloseReason
import com.fserver.net.session.PeerSession
import com.fserver.net.spi.Transport
import com.fserver.net.support.DEAD
import com.fserver.net.support.DeadEndpoint
import com.fserver.net.support.DeadTransport
import com.fserver.net.support.LOOPBACK
import com.fserver.net.support.LoopbackEndpoint
import com.fserver.net.support.LoopbackNetwork
import com.fserver.net.support.TestDictionary
import com.fserver.net.support.TestMessage
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
            alice.connections.connect(discovered("bob", listOf(DEAD, LOOPBACK))).getOrThrow()
        }

        assertEquals(LOOPBACK, session.transport)
        assertEquals(bob.identity.deviceId, session.peer.deviceId)
    }

    @Test
    fun `every route failing surfaces the last failure, not a session`() = runBlocking {
        val alice = node("alice", transports = listOf(DeadTransport()))

        val outcome = withTimeout(TIMEOUT) {
            alice.connections.connect(discovered("bob", listOf(DEAD)))
        }

        assertTrue(outcome.isFailure)
        assertTrue(outcome.exceptionOrNull() is NetworkException.Transport)
        assertTrue(alice.connections.sessions.value.isEmpty())
    }

    @Test
    fun `a peer with no route at all is NoRoute, and so is one no transport carries`() =
        runBlocking {
            val alice = node("alice")

            val routeless = alice.connections.connect(discovered("bob"))
            val uncarried = alice.connections.connect(
                PeerRef("peer-bob", DEAD, DeadEndpoint("bob"))
            )

            assertTrue(routeless.exceptionOrNull() is NetworkException.NoRoute)
            assertTrue(uncarried.exceptionOrNull() is NetworkException.NoRoute)
        }

    @Test
    fun `the session limit is enforced once the handshake is already done`() = runBlocking {
        val alice = node("alice", policy = POLICY.copy(maxSessions = 1))
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
        assertEquals(listOf(kept), alice.connections.sessions.value)
        assertTrue(kept.state.value is PeerSession.State.Ready)
    }

    @Test
    fun `probe reports what the handshake revealed and leaves no session behind`() = runBlocking {
        val alice = node("alice")
        val bob = node("bob")
        acceptEverything(bob)

        val profile = withTimeout(TIMEOUT) {
            alice.connections.probe(PeerRef("peer-bob", LOOPBACK, LoopbackEndpoint("bob")))
                .getOrThrow()
        }

        assertEquals(bob.identity.deviceId, profile.identity.deviceId)
        assertEquals(bob.identity.fingerprint, profile.identity.fingerprint)
        assertEquals(1, profile.negotiated.protocolVersion)
        assertTrue(alice.connections.sessions.value.isEmpty())
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
        assertTrue(alice.connections.sessions.value.isEmpty())
        assertTrue(bob.connections.sessions.value.isEmpty())
    }

    @Test
    fun `disconnect drops the session from both the registry and the peer`() = runBlocking {
        val alice = node("alice")
        val bob = node("bob")
        acceptEverything(bob)

        val session = withTimeout(TIMEOUT) { connect(alice, "bob").getOrThrow() }
        val bobSide = withTimeout(TIMEOUT) { firstSession(bob) }

        alice.connections.disconnect(session.peer.deviceId, CloseReason.Local("done"))

        assertTrue(alice.connections.sessions.value.isEmpty())
        val end = withTimeout(TIMEOUT) { bobSide.state.first { it.isFinal } }
        assertTrue(end is PeerSession.State.Closed)
        assertTrue(bob.connections.sessions.value.isEmpty())
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
            transports = transports,
            policy = policy,
            scope = scope,
        )
    ).also(nodes::add)

    /** A peer as discovery would report it, with one route per transport listed. */
    private fun discovered(name: String, transports: List<Transport.Id> = emptyList()) = DiscoveredPeer(
        deviceId = "peer-$name",
        displayName = name,
        kind = null,
        routes = transports.map { id ->
            val endpoint = if (id == DEAD) DeadEndpoint(name) else LoopbackEndpoint(name)
            PeerRef("peer-$name", id, endpoint)
        },
        advertised = DiscoveredPeer.Advertised(),
        lastSeen = Instant.now(),
    )

    private suspend fun connect(from: NetworkNode<TestMessage>, to: String) =
        from.connections.connect(PeerRef("peer-$to", LOOPBACK, LoopbackEndpoint(to)))

    private suspend fun acceptEverything(node: NetworkNode<TestMessage>): Job =
        collectIncoming(node) { it.accept() }

    /** `incoming` is a hot, replay-free flow: nothing may be dialled before a collector is on it. */
    private suspend fun collectIncoming(
        node: NetworkNode<TestMessage>,
        handle: suspend (ConnectionManager.IncomingRequest) -> Unit,
    ): Job {
        val job = scope.launch { node.connections.incoming.collect { handle(it) } }
        delay(SUBSCRIBE_GRACE)
        return job
    }

    private suspend fun firstSession(node: NetworkNode<TestMessage>): PeerSession<TestMessage> =
        node.connections.sessions.first { it.isNotEmpty() }.first()

    private val PeerSession.State.isFinal: Boolean
        get() = this is PeerSession.State.Closed || this is PeerSession.State.Failed

    private companion object {
        val TIMEOUT = 10.seconds
        val SUBSCRIBE_GRACE = 50.milliseconds

        // Deterministic: no keep-alive timers, no retries.
        val POLICY = ConnectionPolicy(keepAlive = null, reconnect = ReconnectPolicy.None)
    }
}
