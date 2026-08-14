package com.fserver.net.session

import com.fserver.net.config.NetworkConfig
import com.fserver.net.NetworkException
import com.fserver.net.NetworkNode
import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.connection.PeerRef
import com.fserver.net.connection.ReconnectPolicy
import com.fserver.net.dictionary.MessageCodec
import com.fserver.net.dictionary.MessageDictionary
import com.fserver.net.security.identity.EphemeralIdentityStore
import com.fserver.net.session.PeerSession.State
import com.fserver.net.support.LOOPBACK
import com.fserver.net.support.LoopbackEndpoint
import com.fserver.net.support.LoopbackNetwork
import com.fserver.net.support.TestDictionary
import com.fserver.net.support.TestMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** What a session does when the answer, the peer, or the link does not hold up. */
class SessionFailureTest {

    private val network = LoopbackNetwork()
    private val scope = CoroutineScope(SupervisorJob())
    private val nodes = mutableListOf<NetworkNode<TestMessage>>()

    @After
    fun tearDown() {
        nodes.forEach { it.close() }
        scope.cancel()
    }

    @Test
    fun `a request nobody answers fails on its own timeout`() = runBlocking {
        val alice = node("alice")
        val bob = node("bob")
        acceptEverything(bob)

        val session = withTimeout(TIMEOUT) { connect(alice, "bob").getOrThrow() }
        // Bob never collects `incoming`, so the request is delivered and simply never answered.
        val outcome = withTimeout(TIMEOUT) {
            session.request(TestMessage.Ask("hello"), timeout = 200.milliseconds)
        }

        assertTrue(outcome.exceptionOrNull() is NetworkException.RequestTimeout)
        assertTrue(session.state.value is State.Ready)
    }

    @Test
    fun `a lost link fails the request in flight instead of replaying it`() = runBlocking {
        val alice = node("alice")
        val bob = node("bob")
        acceptEverything(bob)

        val session = withTimeout(TIMEOUT) { connect(alice, "bob").getOrThrow() }
        val arrived = Channel<TestMessage>(Channel.UNLIMITED)
        scope.launch {
            firstSession(bob).incoming.collect { arrived.send(it.message) }
        }

        val pending = scope.async { session.request(TestMessage.Ask("hello"), timeout = TIMEOUT) }
        withTimeout(TIMEOUT) { arrived.receive() }
        network.cutLinks()

        val outcome = withTimeout(TIMEOUT) { pending.await() }

        assertTrue(outcome.exceptionOrNull() is NetworkException.SessionLinkLost)
        // Nothing was re-sent: bob saw the question exactly once.
        assertTrue(arrived.tryReceive().isFailure)
    }

    @Test
    fun `a payload the peer's dictionary rejects comes back as a protocol error`() = runBlocking {
        val alice = node("alice", dictionary = PoisonDictionary())
        val bob = node("bob")
        acceptEverything(bob)
        scope.launch { firstSession(bob).incoming.collect { } }

        val session = withTimeout(TIMEOUT) { connect(alice, "bob").getOrThrow() }
        val outcome = withTimeout(TIMEOUT) { session.request(TestMessage.Ask("hello")) }

        val error = outcome.exceptionOrNull()
        assertTrue(error is NetworkException.Protocol)
        assertEquals("malformed payload", error!!.message)
        // A message the peer could not read is not a reason to tear the session down.
        assertTrue(session.state.value is State.Ready)
    }

    @Test
    fun `keep-alive traffic keeps an otherwise idle session up`() = runBlocking {
        val alice = node("alice", policy = KEEPALIVE)
        val bob = node("bob", policy = KEEPALIVE)
        acceptEverything(bob)

        val session = withTimeout(TIMEOUT) { connect(alice, "bob").getOrThrow() }
        val bobSide = withTimeout(TIMEOUT) { firstSession(bob) }

        // Well past the three idle periods that would drop the link if PONGs were not answered.
        delay(600)

        assertTrue(session.state.value is State.Ready)
        assertTrue(bobSide.state.value is State.Ready)
    }

    @Test
    fun `a peer that stops answering keep-alives loses the link`() = runBlocking {
        val alice = node("alice", policy = KEEPALIVE)
        val bob = node("bob", policy = KEEPALIVE)
        acceptEverything(bob)

        val session = withTimeout(TIMEOUT) { connect(alice, "bob").getOrThrow() }
        // The link stays open, but nothing crosses it - what a peer gone silent looks like.
        network.muteLinks()

        val end = withTimeout(TIMEOUT) {
            session.state.first { it is State.Closed || it is State.Failed }
        }

        assertTrue(end is State.Closed && end.reason is CloseReason.LinkLost)
    }

    @Test
    fun `a closed session refuses work instead of queueing it forever`() = runBlocking {
        val alice = node("alice")
        val bob = node("bob")
        acceptEverything(bob)

        val session = withTimeout(TIMEOUT) { connect(alice, "bob").getOrThrow() }
        session.close(CloseReason.Local("done"))

        val sent = withTimeout(TIMEOUT) { session.send(TestMessage.Notice("late")) }
        val asked = withTimeout(TIMEOUT) { session.request(TestMessage.Ask("late")) }

        assertTrue(sent.exceptionOrNull() is NetworkException.SessionClosed)
        assertTrue(asked.exceptionOrNull() is NetworkException.SessionClosed)
    }

    // ------------------------------------------------------------------ helpers

    private fun node(
        name: String,
        dictionary: MessageDictionary<TestMessage> = TestDictionary(),
        policy: ConnectionPolicy = POLICY,
    ): NetworkNode<TestMessage> = NetworkNode.create(
        NetworkConfig(
            dictionary = dictionary,
            identityStore = EphemeralIdentityStore(displayName = name),
            transports = listOf(network.transport(name)),
            policy = policy,
            scope = scope,
        )
    ).also(nodes::add)

    private suspend fun connect(from: NetworkNode<TestMessage>, to: String) =
        from.requestsManager.connect(PeerRef("peer-$to", LOOPBACK, LoopbackEndpoint(to)))

    /** `incoming` is hot and replay-free: nothing may be dialled before a collector is on it. */
    private suspend fun acceptEverything(node: NetworkNode<TestMessage>): Job {
        val job = scope.launch { node.incoming.incoming.collect { it.accept() } }
        delay(50)
        return job
    }

    private suspend fun firstSession(node: NetworkNode<TestMessage>): PeerSession<TestMessage> =
        node.incoming.sessions.first { it.isNotEmpty() }.first()

    /** Speaks the right dictionary on the handshake and nonsense on the wire. */
    private class PoisonDictionary : MessageDictionary<TestMessage> {
        override val descriptor = TestDictionary().descriptor

        override val codec = object : MessageCodec<TestMessage> {
            override fun encode(message: TestMessage) = "?:boom".encodeToByteArray()
            override fun decode(bytes: ByteArray) = error("unreadable")
        }

        override fun negotiate(remote: MessageDictionary.Descriptor) =
            MessageDictionary.Decision.Accept(descriptor.version)
    }

    private companion object {
        val TIMEOUT = 10.seconds

        val POLICY = ConnectionPolicy(keepAlive = null, reconnect = ReconnectPolicy.None)
        val KEEPALIVE = POLICY.copy(keepAlive = 100.milliseconds)
    }
}
