package com.fserver.net.config

import com.fserver.net.NetworkNode
import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.connection.IncomingConnectionsManager
import com.fserver.net.connection.PeerRef
import com.fserver.net.connection.TimeoutsConfig
import com.fserver.net.discovery.PeerAttributes
import com.fserver.net.security.auth.AuthMethod
import com.fserver.net.security.auth.AuthMethodId
import com.fserver.net.security.identity.EphemeralIdentityStore
import com.fserver.net.session.PeerSession
import com.fserver.net.spi.Advertiser
import com.fserver.net.spi.SpiId
import com.fserver.net.spi.Transport
import com.fserver.net.spi.TransportCapabilities
import com.fserver.net.spi.TransportEndpoint
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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * What a config swap does to what the previous config already started. The reconciling is each
 * collaborator's own, so these drive it end to end through [NetworkNode.reloadConfig].
 */
class ConfigReloadTest {

    private val network = LoopbackNetwork()
    private val scope = CoroutineScope(SupervisorJob())
    private val nodes = mutableListOf<NetworkNode<TestMessage>>()

    @After
    fun tearDown() {
        nodes.forEach { it.close() }
        scope.cancel()
    }

    // ------------------------------------------------------------------ sessions

    @Test
    fun `a session on a transport the reload dropped is closed`() = runBlocking {
        val alice = node("alice")
        val bob = node("bob")
        acceptEverything(bob)

        val session = withTimeout(TIMEOUT) { connect(alice, "bob").getOrThrow() }

        alice.reloadConfig(alice.config.copy(transports = listOf(DeadTransport())))

        val end = withTimeout(TIMEOUT) { session.state.first { it.isFinal } }
        assertTrue(end is PeerSession.State.Closed)
    }

    @Test
    fun `a session whose auth method the reload disabled is closed`() = runBlocking {
        val method = TestingAuthMethod()
        val alice = node("alice", authMethods = listOf(method))
        val bob = node("bob")
        acceptEverything(bob)

        val session = withTimeout(TIMEOUT) { connect(alice, "bob").getOrThrow() }

        // Still installed, just switched off - the handshake would no longer offer it, so a
        // session that got in on it has no standing either.
        alice.reloadConfig(alice.config.copy(authMethods = listOf(Disabled(method))))

        val end = withTimeout(TIMEOUT) { session.state.first { it.isFinal } }
        assertTrue(end is PeerSession.State.Closed)
    }

    @Test
    fun `a session the new config still permits is left running`() = runBlocking {
        val alice = node("alice")
        val bob = node("bob")
        acceptEverything(bob)

        val session = withTimeout(TIMEOUT) { connect(alice, "bob").getOrThrow() }

        alice.reloadConfig(
            alice.config.copy(policy = POLICY.copy(timeouts = TimeoutsConfig(keepAlive = null)))
        )

        delay(SETTLE)
        assertTrue(session.state.value is PeerSession.State.Ready)
        assertEquals(listOf(session), alice.incoming.sessions.value)
    }

    @Test
    fun `a handshake that finishes after the reload does not become a session`() = runBlocking {
        val method = TestingAuthMethod()
        val alice = node("alice")
        val bob = node("bob", authMethods = listOf(method))

        // Held rather than answered, so the reload lands squarely in the middle of the handshake.
        // The method was resolved before the swap and runs to completion regardless; what stops
        // it is the registry refusing a session the new config would not admit.
        val held = Channel<IncomingConnectionsManager.IncomingRequest>(Channel.UNLIMITED)
        collectIncoming(bob) { held.send(it) }
        scope.launch { connect(alice, "bob") }

        val request = withTimeout(TIMEOUT) { held.receive() }
        bob.reloadConfig(bob.config.copy(authMethods = listOf(Disabled(method))))

        val accepted = withTimeout(TIMEOUT) { request.accept() }
        assertTrue(accepted.isFailure)
        assertTrue(accepted.exceptionOrNull()!!.message!!.contains("no longer permitted"))
        assertTrue(bob.incoming.sessions.value.isEmpty())
    }

    // ------------------------------------------------------------------ transports

    @Test
    fun `a transport the reload dropped is shut down, one it kept is not`() = runBlocking {
        val dropped = RecordingTransport(network.transport("alice"))
        val kept = RecordingTransport(DeadTransport())
        val alice = node("alice", transports = listOf(dropped, kept))

        alice.reloadConfig(alice.config.copy(transports = listOf(kept)))

        assertEquals(1, dropped.shutdowns.get())
        assertEquals(0, kept.shutdowns.get())
    }

    @Test
    fun `a transport the reload kept is not made to listen again`() = runBlocking {
        val kept = RecordingTransport(network.transport("alice"))
        val alice = node("alice", transports = listOf(kept))
        assertEquals(1, kept.listens.get())

        // Adding a transport must not unbind the one that was already answering.
        alice.reloadConfig(alice.config.copy(transports = listOf(kept, DeadTransport())))

        assertEquals(1, kept.listens.get())
    }

    @Test
    fun `a listener that gave up is started again by the next reload`() = runBlocking {
        val deaf = DeafTransport()
        val alice = node("alice", transports = listOf(network.transport("alice"), deaf))
        withTimeout(TIMEOUT) { while (deaf.listens.get() < 1) delay(10) }
        delay(SETTLE)

        alice.reloadConfig(alice.config.copy(advertisedAttributes = mapOf("kind" to "phone")))

        // The relaunched listener is dispatched, not run inline, so wait for it rather than read
        // the counter the instant the reload returns.
        withTimeout(TIMEOUT) { while (deaf.listens.get() < 2) delay(10) }
    }

    // ------------------------------------------------------------------ advertising

    @Test
    fun `a reload does not put a silent node on the air`() = runBlocking {
        val advertiser = FakeAdvertiser(SpiId("mdns"))
        val alice = node("alice", advertisers = listOf(advertiser))

        // startAdvertising() was never called; a config change is not a request to be findable.
        alice.reloadConfig(alice.config.copy(advertisedAttributes = mapOf("kind" to "phone")))

        delay(SETTLE)
        assertNull(advertiser.payloads.firstOrNull())
    }

    @Test
    fun `a reload that changes the advertisement re-announces it`() = runBlocking {
        val advertiser = FakeAdvertiser(SpiId("mdns"))
        val alice = node("alice", advertisers = listOf(advertiser))
        alice.discovery.startAdvertising().getOrThrow()
        withTimeout(TIMEOUT) { advertiser.awaitPayloads(1) }

        alice.reloadConfig(
            alice.config.copy(
                advertisedAttributes = mapOf(PeerAttributes.KIND to "phone")
            )
        )

        val payload = withTimeout(TIMEOUT) { advertiser.awaitPayloads(2) }.last()
        assertEquals("phone", payload.attributes[PeerAttributes.KIND])
        // The old advertiser was off the air before the new one went on it.
        assertEquals(1, advertiser.cancellations.get())
    }

    @Test
    fun `a reload that says nothing new leaves the advertisers alone`() = runBlocking {
        val advertiser = FakeAdvertiser(SpiId("mdns"))
        val alice = node("alice", advertisers = listOf(advertiser))
        alice.discovery.startAdvertising().getOrThrow()
        withTimeout(TIMEOUT) { advertiser.awaitPayloads(1) }

        // Nothing here reaches the air, so re-announcing would be a gap in visibility for nothing.
        alice.reloadConfig(
            alice.config.copy(policy = POLICY.copy(timeouts = TimeoutsConfig(connect = 1.seconds)))
        )

        delay(SETTLE)
        assertEquals(1, advertiser.payloads.size)
        assertEquals(0, advertiser.cancellations.get())
    }

    // ------------------------------------------------------------------ what cannot change

    @Test
    fun `the identity a session is keyed against cannot be swapped`() = runBlocking {
        val alice = node("alice")

        val outcome = runCatching {
            alice.reloadConfig(
                alice.config.copy(identityStore = EphemeralIdentityStore(displayName = "mallory"))
            )
        }

        assertTrue(outcome.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `reloading on the live config does not stack a second built-in method`() = runBlocking {
        val alice = node("alice")
        val before = alice.config.authMethods.map { it.id }

        alice.reloadConfig(alice.config)
        alice.reloadConfig(alice.config)

        assertEquals(before, alice.config.authMethods.map { it.id })
    }

    // ------------------------------------------------------------------ helpers

    private fun node(
        name: String,
        transports: List<Transport> = listOf(network.transport(name)),
        authMethods: List<AuthMethod> = listOf(TestingAuthMethod()),
        advertisers: List<Advertiser> = emptyList(),
    ): NetworkNode<TestMessage> = NetworkNode.create(
        NetworkConfig(
            dictionary = TestDictionary(),
            identityStore = EphemeralIdentityStore(displayName = name),
            authMethods = authMethods,
            transports = transports,
            advertisers = advertisers,
            policy = POLICY,
            scope = scope,
        )
    ).also(nodes::add)

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

    private val PeerSession.State.isFinal: Boolean
        get() = this is PeerSession.State.Closed || this is PeerSession.State.Failed

    /** Installed but switched off - what the handshake checks and a reload has to check too. */
    private class Disabled(private val delegate: AuthMethod) : AuthMethod by delegate {
        override val id: AuthMethodId = AuthMethodId("disabled-${delegate.id.value}")
    }

    /** Counts what a reload does to a transport, and otherwise is the transport. */
    private class RecordingTransport(private val delegate: Transport) : Transport by delegate {
        val shutdowns = AtomicInteger()
        val listens = AtomicInteger()

        override val listener: Transport.Listener? = delegate.listener?.let { inner ->
            object : Transport.Listener {
                override fun listen(): Flow<Transport.InboundConnection> {
                    listens.incrementAndGet()
                    return inner.listen()
                }
            }
        }

        override suspend fun shutdown() {
            shutdowns.incrementAndGet()
            delegate.shutdown()
        }
    }

    /** A listener that gives up at once, as a radio losing its permission would. */
    private class DeafTransport : Transport {
        val listens = AtomicInteger()

        override val id: SpiId = SpiId("deaf")
        override val capabilities = TransportCapabilities()

        override val listener = object : Transport.Listener {
            override fun listen(): Flow<Transport.InboundConnection> = flow {
                listens.incrementAndGet()
                throw IOException("listener gave up")
            }
        }

        override fun supports(endpoint: TransportEndpoint) = false

        override suspend fun open(endpoint: TransportEndpoint): Result<Transport.Channel> =
            Result.failure(IOException("deaf transport"))

        override suspend fun shutdown() = Unit
    }

    private class FakeAdvertiser(override val id: SpiId) : Advertiser {
        val payloads = CopyOnWriteArrayList<Advertiser.Payload>()
        val cancellations = AtomicInteger()

        override suspend fun advertise(payload: Advertiser.Payload): Flow<Advertiser.Event> = flow {
            payloads += payload
            emit(Advertiser.Event.Started)
            try {
                awaitCancellation()
            } finally {
                cancellations.incrementAndGet()
            }
        }

        suspend fun awaitPayloads(count: Int): List<Advertiser.Payload> {
            while (payloads.size < count) delay(10)
            return payloads.toList()
        }
    }

    private companion object {
        val TIMEOUT = 10.seconds
        val SUBSCRIBE_GRACE = 50.milliseconds
        val SETTLE = 300.milliseconds

        // Deterministic: no keep-alive timers, no retries.
        val POLICY = ConnectionPolicy(timeouts = TimeoutsConfig(keepAlive = null), reconnect = null)
    }
}
