package com.fserver.net

import com.fserver.net.config.NetworkConfig
import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.connection.ReconnectPolicy
import com.fserver.net.connection.PeerRef
import com.fserver.net.connection.TimeoutsConfig
import com.fserver.net.dictionary.MessageDictionary
import com.fserver.net.security.identity.EphemeralIdentityStore
import com.fserver.net.security.PeerAuthenticator
import com.fserver.net.session.PeerSession
import com.fserver.net.session.CloseReason
import com.fserver.net.session.PeerSession.State
import com.fserver.net.support.LOOPBACK
import com.fserver.net.support.LoopbackEndpoint
import com.fserver.net.support.LoopbackNetwork
import com.fserver.net.support.TestDictionary
import com.fserver.net.support.TestMessage
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
    fun `both ends see one session, and identities match`() = runBlocking {
        val alice = node("alice")
        val bob = node("bob")
        acceptEverything(bob)

        val session = withTimeout(TIMEOUT) { connect(alice, "bob").getOrThrow() }
        val bobSide = withTimeout(TIMEOUT) { firstSession(bob) }

        assertEquals(bob.identity.deviceId, session.negotiatedDeviceId)
        assertEquals(alice.identity.deviceId, bobSide.negotiatedDeviceId)
        assertEquals(1, alice.incoming.sessions.value.size)
        assertEquals(1, bob.incoming.sessions.value.size)
    }

    @Test
    fun `connecting twice reuses the session`() = runBlocking {
        val alice = node("alice")
        val bob = node("bob")
        acceptEverything(bob)

        val first = withTimeout(TIMEOUT) { connect(alice, "bob", deviceId = bob.identity.deviceId).getOrThrow() }
        val second = withTimeout(TIMEOUT) { connect(alice, "bob", deviceId = bob.identity.deviceId).getOrThrow() }

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
        val bob = node("bob", authenticator = { _, _ -> PeerAuthenticator.Decision.Reject("unknown device") })

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

        val session = withTimeout(TIMEOUT) { connect(alice, "bob", deviceId = "peer-bob").getOrThrow() }
        withTimeout(TIMEOUT) { firstSession(bob) }

        network.cutLinks()

        // Same instance, back in Ready: a held reference stays valid across a reconnect.
        withTimeout(TIMEOUT) { session.state.first { it is State.Connecting } }
        withTimeout(TIMEOUT) { session.state.first { it is State.Ready } }
        assertTrue(alice.incoming.sessions.value.contains(session))
    }

    // ------------------------------------------------------------------ helpers

    private fun node(
        name: String,
        dictionary: MessageDictionary<TestMessage> = TestDictionary(),
        authenticator: PeerAuthenticator? = null,
        // Deterministic by default: no timers, no retries.
        policy: ConnectionPolicy = ConnectionPolicy(timeouts = TimeoutsConfig(keepAlive = null), reconnect = null),
    ): NetworkNode<TestMessage> = NetworkNode.create(
        NetworkConfig(
            dictionary = dictionary,
            identityStore = EphemeralIdentityStore(displayName = name),
            transports = listOf(network.transport(name)),
            authenticator = authenticator,
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
    }
}
