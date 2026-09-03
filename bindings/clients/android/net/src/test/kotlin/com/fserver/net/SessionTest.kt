package com.fserver.net

import com.fserver.common.exception.NetworkException
import com.fserver.net.config.NetworkConfig
import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.connection.SessionConfig
import com.fserver.net.connection.PeerRef
import com.fserver.net.connection.ReconnectPolicy
import com.fserver.net.connection.TimeoutsConfig
import com.fserver.net.dictionary.MessageDictionary
import com.fserver.net.security.PeerAuthenticator
import com.fserver.net.security.identity.EphemeralIdentityStore
import com.fserver.net.session.CloseReason
import com.fserver.net.session.PeerSession
import com.fserver.net.session.PeerSession.State
import com.fserver.net.support.LOOPBACK
import com.fserver.net.support.LoopbackEndpoint
import com.fserver.net.support.LoopbackNetwork
import com.fserver.net.support.TestDictionary
import com.fserver.net.support.TestMessage
import com.fserver.net.support.TestingAuthMethod
import com.fserver.net.wire.Envelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SessionTest {

    private val network = LoopbackNetwork()
    private val scope = CoroutineScope(SupervisorJob())
    private val nodes = mutableListOf<NetworkNode<TestMessage>>()

    @After
    fun tearDown() {
        nodes.forEach { it.close() }
        scope.cancel()
    }

    @Test
    fun `request gets an answer built from the same dictionary`() = runBlocking {
        val alice = node("alice")
        val bob = node("bob")
        acceptEverything(bob)

        // Bob answers questions; nothing here ever sees a byte array.
        scope.launch {
            val session = firstSession(bob)
            session.incoming.collect { inbound ->
                val ask = inbound.message as TestMessage.Ask
                inbound.reply?.invoke(TestMessage.Answer(ask.text.uppercase()))
            }
        }

        val session = withTimeout(TIMEOUT) { connect(alice, "bob").getOrThrow() }
        val answer = withTimeout(TIMEOUT) { session.request(TestMessage.Ask("ping")).getOrThrow() }

        assertEquals(TestMessage.Answer("PING"), answer)
        assertTrue(session.state.value is State.Ready)
    }

    @Test
    fun `fire and forget message arrives`() = runBlocking {
        val alice = node("alice")
        val bob = node("bob")
        acceptEverything(bob)

        val session = withTimeout(TIMEOUT) { connect(alice, "bob").getOrThrow() }
        session.send(TestMessage.Notice("hello")).getOrThrow()

        val received = withTimeout(TIMEOUT) { firstSession(bob).incoming.first() }

        assertEquals(TestMessage.Notice("hello"), received.message)
        assertEquals(null, received.reply)
    }

    @Test
    fun `payload budget is the frame limit less the envelope and the seal`() = runBlocking {
        val alice = node("alice")
        val bob = node("bob")
        acceptEverything(bob)

        val session = withTimeout(TIMEOUT) { connect(alice, "bob").getOrThrow() }

        // Passthrough crypto seals nothing, so the envelope header is the whole difference.
        assertEquals(64 * 1024 - Envelope.Codec.HEADER_SIZE, session.maxPayloadSize)
    }

    @Test
    fun `a message filling the budget still goes through`() = runBlocking {
        val alice = node("alice")
        val bob = node("bob")
        acceptEverything(bob)

        val session = withTimeout(TIMEOUT) { connect(alice, "bob").getOrThrow() }
        val notice = TestMessage.Notice("x".repeat(session.maxPayloadSize - NOTICE_PREFIX))

        session.send(notice).getOrThrow()

        val received = withTimeout(TIMEOUT) { firstSession(bob).incoming.first() }
        assertEquals(notice, received.message)
    }

    @Test
    fun `a message larger than one frame is split and rebuilt`() = runBlocking {
        val alice = node("alice")
        val bob = node("bob")
        acceptEverything(bob)

        val session = withTimeout(TIMEOUT) { connect(alice, "bob").getOrThrow() }
        val notice = TestMessage.Notice(body(session.maxPayloadSize * 3 + 17))

        session.send(notice).getOrThrow()

        val received = withTimeout(TIMEOUT) { firstSession(bob).incoming.first() }
        assertEquals(notice, received.message)
    }

    @Test
    fun `a split request is answered with a split response`() = runBlocking {
        val alice = node("alice")
        val bob = node("bob")
        acceptEverything(bob)

        scope.launch {
            val session = firstSession(bob)
            session.incoming.collect { inbound ->
                val ask = inbound.message as TestMessage.Ask
                inbound.reply?.invoke(TestMessage.Answer(ask.text.uppercase()))
            }
        }

        val session = withTimeout(TIMEOUT) { connect(alice, "bob").getOrThrow() }
        val ask = TestMessage.Ask(body(session.maxPayloadSize * 2))

        val answer = withTimeout(TIMEOUT) { session.request(ask).getOrThrow() }

        assertEquals(TestMessage.Answer(ask.text.uppercase()), answer)
        assertTrue(session.state.value is State.Ready)
    }

    @Test
    fun `a message past the configured ceiling is refused, and the link survives it`() =
        runBlocking {
            val alice = node("alice", policy = capped(CEILING))
            val bob = node("bob")
            acceptEverything(bob)

            val session = withTimeout(TIMEOUT) { connect(alice, "bob").getOrThrow() }

            val outcome = session.send(TestMessage.Notice(body(CEILING + 1)))

            assertTrue(outcome.exceptionOrNull() is NetworkException.FrameTooLarge)
            assertTrue(session.state.value is State.Ready)

            // Nothing of it reached the wire, so the session is still worth something.
            session.send(TestMessage.Notice("still here")).getOrThrow()
            val received = withTimeout(TIMEOUT) { firstSession(bob).incoming.first() }
            assertEquals(TestMessage.Notice("still here"), received.message)
        }

    @Test
    fun `a message past the receiver's ceiling is dropped, and the link survives it`() =
        runBlocking {
            val alice = node("alice")
            val bob = node("bob", policy = capped(CEILING))
            acceptEverything(bob)

            val session = withTimeout(TIMEOUT) { connect(alice, "bob").getOrThrow() }

            session.send(TestMessage.Notice(body(CEILING * 2))).getOrThrow()
            session.send(TestMessage.Notice("still here")).getOrThrow()

            // The big one is gathered and thrown away, so the small one is what bob ever sees.
            val received = withTimeout(TIMEOUT) { firstSession(bob).incoming.first() }
            assertEquals(TestMessage.Notice("still here"), received.message)
            assertTrue(session.state.value is State.Ready)
        }

    @Test
    fun `both ends see one session, and identities match`() = runBlocking {
        val alice = node("alice")
        val bob = node("bob")
        acceptEverything(bob)

        val session = withTimeout(TIMEOUT) { connect(alice, "bob").getOrThrow() }
        val bobSide = withTimeout(TIMEOUT) { firstSession(bob) }

        assertEquals(bob.config.identityStore.local().deviceId, session.negotiatedDeviceId)
        assertEquals(alice.config.identityStore.local().deviceId, bobSide.negotiatedDeviceId)
        assertEquals(1, alice.incoming.sessions.value.size)
        assertEquals(1, bob.incoming.sessions.value.size)
    }

    @Test
    fun `connecting twice reuses the session`() = runBlocking {
        val alice = node("alice")
        val bob = node("bob")
        acceptEverything(bob)

        val first = withTimeout(TIMEOUT) {
            connect(
                alice,
                "bob",
                deviceId = bob.config.identityStore.local().deviceId
            ).getOrThrow()
        }
        val second = withTimeout(TIMEOUT) {
            connect(
                alice,
                "bob",
                deviceId = bob.config.identityStore.local().deviceId
            ).getOrThrow()
        }

        assertTrue(first === second)
        assertEquals(1, alice.incoming.sessions.value.size)
    }

    @Test
    fun `a foreign dictionary never gets a session`() = runBlocking {
        val alice = node("alice")
        val bob = node("bob", dictionary = TestDictionary(id = "other.dictionary"))
        acceptEverything(bob)

        val outcome = withTimeout(TIMEOUT) { connect(alice, "bob") }

        assertTrue(outcome.isFailure)
        assertTrue(outcome.exceptionOrNull() is NetworkException.Handshake)
        assertTrue(alice.incoming.sessions.value.isEmpty())
    }

    @Test
    fun `an authenticator that refuses stops the handshake`() = runBlocking {
        val alice = node("alice")
        val bob = node(
            "bob",
            authenticator = { PeerAuthenticator.Decision.Reject("unknown device") })

        acceptEverything(bob)

        val outcome = withTimeout(TIMEOUT) { connect(alice, "bob") }

        assertTrue(outcome.isFailure)
        assertTrue(bob.incoming.sessions.value.isEmpty())
    }

    @Test
    fun `closing one end tears down the other`() = runBlocking {
        val alice = node("alice")
        val bob = node("bob")
        acceptEverything(bob)

        val session = withTimeout(TIMEOUT) { connect(alice, "bob").getOrThrow() }
        val bobSide = withTimeout(TIMEOUT) { firstSession(bob) }

        session.close(CloseReason.Normal)

        withTimeout(TIMEOUT) {
            bobSide.state.first { it is State.Closed || it is State.Failed }
        }
        assertTrue(alice.incoming.sessions.value.isEmpty())
    }

    @Test
    fun `a cut link is rebuilt and the session object survives it`() = runBlocking {
        val alice = node("alice", policy = reconnecting)
        val bob = node("bob")
        acceptEverything(bob)

        val session =
            withTimeout(TIMEOUT) { connect(alice, "bob", deviceId = "peer-bob").getOrThrow() }
        withTimeout(TIMEOUT) { firstSession(bob) }

        network.cutLinks()

        // Same instance, back in Ready: a held reference stays valid across a reconnect.
        withTimeout(TIMEOUT) { session.state.first { it is State.Connecting } }
        withTimeout(TIMEOUT) { session.state.first { it is State.Ready } }
        assertTrue(alice.incoming.sessions.value.contains(session))
    }

    // ------------------------------------------------------------------ helpers

    /** A body of exactly [size] bytes once the codec has written its prefix in front of it. */
    private fun body(size: Int): String = "x".repeat(size - NOTICE_PREFIX)

    private fun capped(ceiling: Int): ConnectionPolicy = ConnectionPolicy(
        timeouts = TimeoutsConfig(keepAlive = null),
        reconnect = null,
        sessionConfig = SessionConfig(maxAssembledMessageSize = ceiling),
    )

    private fun node(
        name: String,
        dictionary: MessageDictionary<TestMessage> = TestDictionary(),
        authenticator: PeerAuthenticator? = null,
        // Deterministic by default: no timers, no retries.
        policy: ConnectionPolicy = ConnectionPolicy(
            timeouts = TimeoutsConfig(keepAlive = null),
            reconnect = null
        ),
    ): NetworkNode<TestMessage> = NetworkNode.create(
        NetworkConfig(
            dictionary = dictionary,
            identityStore = EphemeralIdentityStore(displayName = name),
            transports = listOf(network.transport(name)),
            authenticator = authenticator,
            authMethods = listOf(TestingAuthMethod()),
            policy = policy,
            scope = scope,
        )
    ).also(nodes::add)

    private val reconnecting = ConnectionPolicy(
        timeouts = TimeoutsConfig(keepAlive = null),
        reconnect = ReconnectPolicy.ExponentialBackoff(
            initialDelay = 50.milliseconds,
            maxDelay = 200.milliseconds,
            maxAttempts = 5,
        ),
    )

    private suspend fun connect(
        from: NetworkNode<TestMessage>,
        to: String,
        deviceId: String = "peer-$to",
    ) = from.requestsManager.connect(PeerRef(deviceId, LOOPBACK, LoopbackEndpoint(to)))

    private fun acceptEverything(node: NetworkNode<TestMessage>): Job = scope.launch {
        node.incoming.incoming.collect { it.accept() }
    }

    private suspend fun firstSession(node: NetworkNode<TestMessage>): PeerSession<TestMessage> =
        node.incoming.sessions.first { it.isNotEmpty() }.first()

    /** The id the handshake proved - not necessarily [PeerSession.route]'s, which is what was dialed. */
    private val PeerSession<*>.negotiatedDeviceId: String
        get() = (state.value as State.Ready).negotiated.peer.deviceId

    private companion object {
        val TIMEOUT = 10.seconds

        /** What `TestCodec` writes in front of a notice - "N:". */
        const val NOTICE_PREFIX = 2

        /** Small enough to cross with a handful of frames, big enough to need several. */
        const val CEILING = 128 * 1024
    }
}
