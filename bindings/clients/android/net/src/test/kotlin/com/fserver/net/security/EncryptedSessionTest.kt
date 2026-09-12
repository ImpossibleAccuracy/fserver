package com.fserver.net.security

import com.fserver.net.config.NetworkConfig
import com.fserver.net.NetworkNode
import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.connection.PeerRef
import com.fserver.net.connection.TimeoutsConfig
import com.fserver.net.security.crypto.PassthroughCryptoProvider
import com.fserver.net.security.identity.EphemeralIdentityStore
import com.fserver.net.session.PeerSession
import com.fserver.net.support.LOOPBACK
import com.fserver.net.support.LoopbackEndpoint
import com.fserver.net.support.LoopbackNetwork
import com.fserver.net.support.TestDictionary
import com.fserver.net.support.TestMessage
import com.fserver.net.support.TestingAuthMethod
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
            scope.launch { bob.incoming.incoming.collect { it.accept() } }
            delay(50)

            val session = withTimeout(TIMEOUT) {
                alice.requestsManager.connect(PeerRef("peer-bob", LOOPBACK, LoopbackEndpoint("bob")))
                    .getOrThrow()
            }
            session.send(TestMessage.Notice(SECRET)).getOrThrow()

            val received = withTimeout(TIMEOUT) {
                bob.incoming.sessions.first { it.isNotEmpty() }.first().incoming.first()
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

    @Test
    fun `the descriptor never reaches the wire in the clear`() = runBlocking {
        val alice = node("alice")
        val bob = node(DESCRIBED)
        scope.launch { bob.incoming.incoming.collect { it.accept() } }
        delay(50)

        withTimeout(TIMEOUT) {
            alice.requestsManager
                .connect(PeerRef("peer-bob", LOOPBACK, LoopbackEndpoint(DESCRIBED)))
                .getOrThrow()
        }

        // `connect` returns once the session is up; registering it is the node's own work, so the
        // exchange is only certainly over once it shows up here.
        withTimeout(TIMEOUT) { while (alice.incoming.sessions.value.isEmpty()) delay(10) }

        // The name is descriptor data, so it may only ever travel sealed. Seeing it here would
        // mean the exchange slipped back ahead of the seal.
        assertFalse(network.wireFrames.any { it.readable().contains(DESCRIBED) })
        // ...and it did arrive, so this is not passing because nothing was exchanged.
        assertEquals(
            DESCRIBED,
            alice.incoming.sessions.value.single().descriptor.displayName,
        )
    }

    private fun node(name: String): NetworkNode<TestMessage> = NetworkNode.create(
        NetworkConfig(
            dictionary = TestDictionary(),
            identityStore = EphemeralIdentityStore(displayName = name),
            transports = listOf(network.transport(name)),
            crypto = XorCryptoProvider(),
            authMethods = listOf(TestingAuthMethod(crypto = XorCryptoProvider())),
            policy = ConnectionPolicy(timeouts = TimeoutsConfig(keepAlive = null), reconnect = null),
            scope = scope,
        )
    ).also(nodes::add)

    private fun ByteArray.readable() = String(this, Charsets.ISO_8859_1)

    private companion object {
        val TIMEOUT = 10.seconds
        const val SECRET = "top-secret-payload"

        /** Distinctive enough that finding it on the wire cannot be a coincidence. */
        const val DESCRIBED = "bob-the-named-device"
    }
}
