package com.fserver.net.security

import com.fserver.net.config.NetworkConfig
import com.fserver.net.NetworkNode
import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.connection.PeerRef
import com.fserver.net.connection.ReconnectPolicy
import com.fserver.net.session.PeerSession
import com.fserver.net.support.LOOPBACK
import com.fserver.net.support.LoopbackEndpoint
import com.fserver.net.support.LoopbackNetwork
import com.fserver.net.support.TestDictionary
import com.fserver.net.support.TestMessage
import com.fserver.net.support.XorCryptoProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * The crypto seam, end to end: the two ends must agree on a secret through the handshake, use
 * opposite roles, and every session frame must actually go through the provider.
 */
class EncryptedSessionTest {

    private val network = LoopbackNetwork()
    private val scope = CoroutineScope(SupervisorJob())
    private val nodes = mutableListOf<NetworkNode<TestMessage>>()

    @After
    fun tearDown() {
        nodes.forEach { it.close() }
        scope.cancel()
    }

    @Test
    fun `messages cross an encrypting provider and nothing readable reaches the wire`() =
        runBlocking {
            val alice = node("alice")
            val bob = node("bob")
            scope.launch { bob.connections.incoming.collect { it.accept() } }
            delay(50)

            val session = withTimeout(TIMEOUT) {
                alice.connections.connect(PeerRef("peer-bob", LOOPBACK, LoopbackEndpoint("bob")))
                    .getOrThrow()
            }
            session.send(TestMessage.Notice(SECRET)).getOrThrow()

            val received = withTimeout(TIMEOUT) {
                bob.connections.sessions.first { it.isNotEmpty() }.first().incoming.first()
            }

            assertEquals(TestMessage.Notice(SECRET), received.message)
            assertEquals(
                "xor-test",
                (session.state.value as PeerSession.State.Ready).negotiated.cipherSuite.name,
            )
            // If the session wrote around the AEAD instead of through it, the payload would be here.
            assertFalse(network.wireFrames.any { it.readable().contains(SECRET) })
            assertTrue(network.wireFrames.isNotEmpty())
        }

    private fun node(name: String): NetworkNode<TestMessage> = NetworkNode.create(
        NetworkConfig(
            dictionary = TestDictionary(),
            identityStore = EphemeralIdentityStore(displayName = name),
            transports = listOf(network.transport(name)),
            crypto = XorCryptoProvider(),
            policy = ConnectionPolicy(keepAlive = null, reconnect = ReconnectPolicy.None),
            scope = scope,
        )
    ).also(nodes::add)

    private fun ByteArray.readable() = String(this, Charsets.ISO_8859_1)

    private companion object {
        val TIMEOUT = 10.seconds
        const val SECRET = "top-secret-payload"
    }
}
