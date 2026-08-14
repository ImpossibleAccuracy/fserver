package com.fserver.net.handshake

import com.fserver.net.NetworkException
import com.fserver.net.security.PeerAuthenticator
import com.fserver.net.security.auth.AuthContext
import com.fserver.net.security.auth.AuthMethod
import com.fserver.net.security.auth.AuthMethodId
import com.fserver.net.security.auth.AuthOutcome
import com.fserver.net.security.auth.HandshakeIo
import com.fserver.net.security.crypto.CryptoProvider
import com.fserver.net.security.identity.EphemeralIdentityStore
import com.fserver.net.security.identity.PeerIdentity
import com.fserver.net.spi.Transport
import com.fserver.net.spi.TransportCapabilities
import com.fserver.net.support.TEST_POLICY
import com.fserver.net.support.TestDictionary
import com.fserver.net.support.XorCryptoProvider
import com.fserver.net.support.channelPair
import com.fserver.net.support.handshake
import com.fserver.net.support.negotiate
import com.fserver.net.support.negotiator
import com.fserver.net.wire.ByteWriter
import com.fserver.net.wire.Envelope
import com.fserver.net.wire.FrameKind
import com.fserver.net.wire.ProtocolVersions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The two ends of one handshake, driven directly - no manager, no session, so what each side
 * concludes is visible on its own.
 */
class HandshakeNegotiatorTest {

    private val scope = CoroutineScope(SupervisorJob())

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `both ends settle on the highest version they share`() = runBlocking {
        val (initiator, responder) = scope.handshake(
            initiator = negotiator("alice", versions = 1..2),
            responder = negotiator("bob", versions = 1..1),
        )

        assertEquals(1, initiator.getOrThrow().negotiated.protocolVersion)
        assertEquals(1, responder.getOrThrow().negotiated.protocolVersion)
    }

    @Test
    fun `no shared version fails on both ends, and the peer is told why`() = runBlocking {
        val (initiator, responder) = scope.handshake(
            initiator = negotiator("alice", versions = 1..1),
            responder = negotiator("bob", versions = 3..4),
        )

        val refused = responder.exceptionOrNull()
        assertTrue(refused is NetworkException.Handshake)
        assertTrue(refused!!.message!!.contains("no common protocol version"))

        // The initiator learns the reason instead of watching the link go quiet.
        val rejected = initiator.exceptionOrNull()
        assertTrue(rejected is NetworkException.Handshake)
        assertTrue(rejected!!.message!!.contains("no common protocol version"))
    }

    @Test
    fun `the smaller of the two frame sizes is the one that binds`() = runBlocking {
        val (initiator, responder) = scope.handshake(
            initiatorCapabilities = TransportCapabilities(maxFrameSize = 64 * 1024),
            responderCapabilities = TransportCapabilities(maxFrameSize = 16 * 1024),
        )

        assertEquals(16 * 1024, initiator.getOrThrow().negotiated.maxFrameSize)
        assertEquals(16 * 1024, responder.getOrThrow().negotiated.maxFrameSize)
    }

    @Test
    fun `the dictionary's verdict, not the network's, sets the effective version`() = runBlocking {
        val (initiator, responder) = scope.handshake(
            initiator = negotiator(
                "alice",
                dictionary = TestDictionary(version = 2, supported = 1..2)
            ),
            responder = negotiator(
                "bob",
                dictionary = TestDictionary(version = 1, supported = 1..2)
            ),
        )

        assertEquals(1, initiator.getOrThrow().negotiated.dictionaryVersion)
        assertEquals(1, responder.getOrThrow().negotiated.dictionaryVersion)
    }

    @Test
    fun `a dictionary version the peer cannot serve is refused with the descriptor it saw`() =
        runBlocking {
            val (initiator, responder) = scope.handshake(
                initiator = negotiator(
                    "alice",
                    dictionary = TestDictionary(version = 7, supported = 7..7)
                ),
                responder = negotiator(
                    "bob",
                    dictionary = TestDictionary(version = 1, supported = 1..1)
                ),
            )

            val refused = responder.exceptionOrNull()
            assertTrue(refused is NetworkException.DictionaryMismatch)
            assertEquals(7, (refused as NetworkException.DictionaryMismatch).remote.version)
            assertTrue(initiator.exceptionOrNull() is NetworkException.Handshake)
        }

    @Test
    fun `a peer the authenticator refuses is told so, and neither end gets a link`() = runBlocking {
        val (initiator, responder) = scope.handshake(
            responder = negotiator(
                "bob",
                authenticator = { _, _ -> PeerAuthenticator.Decision.Reject("not paired") },
            ),
        )

        assertTrue(responder.exceptionOrNull() is NetworkException.AuthenticationRejected)
        val rejected = initiator.exceptionOrNull()
        assertTrue(rejected is NetworkException.Handshake)
        assertTrue(rejected!!.message!!.contains("not paired"))
    }

    @Test
    fun `the authenticator sees the identity and a comparable code`() =
        runBlocking {
            // confirm-dh ignores whatever raw string the transport hands it (there is none, on a
            // plain socket) and derives its own fingerprint from both sides' identity keys instead.
            var seenCode: String? = null
            var seenDeviceId: String? = null
            val alice = EphemeralIdentityStore(displayName = "alice")

            scope.handshake(
                initiator = negotiator("alice", identityStore = alice),
                responder = negotiator("bob", authenticator = { candidate, code ->
                    seenDeviceId = candidate.deviceId
                    seenCode = code
                    PeerAuthenticator.Decision.Trust
                }),
                responderConfirmationCode = "4821",
            )

            assertEquals(alice.local.deviceId, seenDeviceId)
            assertTrue(seenCode != null && seenCode != "4821")
            assertTrue(seenCode!!.matches(Regex("[0-9a-f]{4}( [0-9a-f]{4}){3}")))
        }

    // ------------------------------------------------------------------ what the public half says

    @Test
    fun `the public hello names nothing about this device`() = runBlocking {
        val alice = EphemeralIdentityStore(displayName = "alice-the-named-device")
        val (ours, theirs) = channelPair()
        scope.negotiate(
            negotiator("alice", identityStore = alice),
            ours,
            CryptoProvider.Role.Initiator,
        )

        val envelope = Envelope.Codec.decode(withTimeout(TIMEOUT) { theirs.inbound.first() })
        assertEquals(FrameKind.HELLO, envelope.kind)

        // Anyone who merely dialled the address gets versions and method names, and nothing that
        // says which device answered - not the id, not the name, not the long-term key.
        val readable = String(envelope.payload, Charsets.ISO_8859_1)
        assertFalse(readable.contains(alice.local.deviceId))
        assertFalse(readable.contains(alice.local.displayName))
        assertFalse(envelope.payload.contains(alice.local.publicKey))
    }

    // ------------------------------------------------------------------ method selection

    @Test
    fun `a transport-backed method is not offered beside anything a plain socket can run`() =
        runBlocking {
            // Alice's transport protects itself, so that is the only method she will run; Bob's
            // does not, so he will not run it at all. There is nothing left in common.
            val (initiator, responder) = scope.handshake(
                initiatorCapabilities = TransportCapabilities(
                    security = AuthMethodId.NEARBY_SAS
                ),
                initiatorConfirmationCode = "4821",
            )

            val refused = initiator.exceptionOrNull()
            assertTrue(refused is NetworkException.Handshake)
            assertTrue(refused!!.message!!.contains("no common auth method"))
            assertTrue(responder.isFailure)
        }

    @Test
    fun `a method the peer names but this transport does not back is refused`() = runBlocking {
        // The check has to hold against a peer that ignores what it was offered, so this side is
        // driven by hand rather than by a negotiator that would play fair.
        val (mallory, bob) = channelPair()
        val responder = scope.negotiate(negotiator("bob"), bob, CryptoProvider.Role.Responder)

        mallory.frame(FrameKind.HELLO, forgedHello().encode())
        mallory.frame(
            FrameKind.AUTH,
            ByteWriter(64)
                .string(AuthMethodId.NEARBY_SAS.value)
                .bytes(ByteArray(0))
                .toByteArray(),
        )

        val error = withTimeout(TIMEOUT) { responder.await() }.exceptionOrNull()
        assertTrue(error is NetworkException.Handshake)
        assertTrue(error!!.message!!.contains("not offered on this transport"))
    }

    // ------------------------------------------------------------------ deadlines and limits

    @Test
    fun `a confirmation slower than the handshake deadline still lands`() = runBlocking {
        // Someone reading a dialog is not a stalled network: the decision sits between two auth
        // frames, so it is the auth deadline that applies and the connection survives.
        val (initiator, responder) = scope.handshake(
            responder = negotiator("bob", authenticator = { _, _ ->
                delay(600)
                PeerAuthenticator.Decision.Trust
            }),
            policy = TEST_POLICY.copy(timeouts = TEST_POLICY.timeouts.copy(handshake = 200.milliseconds)),
        )

        assertTrue(initiator.isSuccess)
        assertTrue(responder.isSuccess)
    }

    @Test
    fun `a method that will not finish is cut off at the round limit`() = runBlocking {
        val (initiator, _) = scope.handshake(
            initiator = negotiator("alice", authMethods = listOf(Endless)),
            responder = negotiator("bob", authMethods = listOf(Endless)),
            policy = TEST_POLICY.copy(authConfig = TEST_POLICY.authConfig.copy(maxAuthRounds = 2)),
        )

        val error = initiator.exceptionOrNull()
        assertTrue(error is NetworkException.Handshake)
        assertTrue(error!!.message!!.contains("ran past 2 rounds"))
    }

    // ------------------------------------------------------------------ transcript binding

    @Test
    fun `both ends fold the very same hellos into the key`() = runBlocking {
        val (initiator, responder) = scope.handshake(
            initiator = negotiator(
                "alice",
                authMethods = listOf(PrologueBound()),
                crypto = XorCryptoProvider()
            ),
            responder = negotiator(
                "bob",
                authMethods = listOf(PrologueBound()),
                crypto = XorCryptoProvider()
            ),
        )

        // The key is nothing but the prologue, so the sealed half only decodes if the two sides
        // saw byte-identical hellos.
        assertTrue(initiator.isSuccess)
        assertTrue(responder.isSuccess)
    }

    @Test
    fun `a hello that differs by one byte breaks the session instead of passing`() = runBlocking {
        val (initiator, responder) = scope.handshake(
            initiator = negotiator(
                "alice",
                authMethods = listOf(PrologueBound(tampered = true)),
                crypto = XorCryptoProvider(),
            ),
            responder = negotiator(
                "bob",
                authMethods = listOf(PrologueBound()),
                crypto = XorCryptoProvider()
            ),
        )

        // No comparison step rejects this: the keys simply differ, so the first sealed frame is
        // unreadable and the handshake cannot finish.
        assertTrue(initiator.isFailure)
        assertTrue(responder.isFailure)
    }

    // ------------------------------------------------------------------ malformed input

    @Test
    fun `a frame of the wrong kind ends the handshake instead of being read as a hello`() =
        runBlocking {
            val (theirs, ours) = channelPair()
            val responder = scope.negotiate(negotiator("bob"), ours, CryptoProvider.Role.Responder)

            theirs.frame(FrameKind.READY)

            val error = withTimeout(TIMEOUT) { responder.await() }.exceptionOrNull()
            assertTrue(error is NetworkException.Handshake)
            assertTrue(error!!.message!!.contains("expected HELLO"))
        }

    @Test
    fun `a peer that never answers fails the handshake on the timeout, not on the link`() =
        runBlocking {
            val (_, ours) = channelPair()
            val responder = scope.negotiate(
                negotiator("bob"),
                ours,
                CryptoProvider.Role.Responder,
                policy = TEST_POLICY.copy(timeouts = TEST_POLICY.timeouts.copy(handshake = 100.milliseconds)),
            )

            val outcome = withTimeout(TIMEOUT) { responder.await() }
            assertTrue(outcome.exceptionOrNull() is NetworkException.Handshake)
        }

    // ------------------------------------------------------------------ helpers

    private suspend fun Transport.Channel.frame(
        kind: FrameKind,
        payload: ByteArray = ByteArray(0),
    ) = send(
        Envelope.Codec.encode(
            Envelope(ProtocolVersions.CURRENT, kind, messageId = 0, payload = payload)
        )
    ).getOrThrow()

    /** Byte-level `contains`, for checking a key is absent rather than merely unreadable. */
    private fun ByteArray.contains(needle: ByteArray): Boolean =
        needle.isNotEmpty() && (0..size - needle.size).any { at ->
            needle.indices.all { this[at + it] == needle[it] }
        }

    private fun forgedHello() = PublicHello(
        minVersion = ProtocolVersions.SUPPORTED.first,
        maxVersion = ProtocolVersions.SUPPORTED.last,
        methods = listOf(AuthMethodId.NEARBY_SAS),
    )

    /**
     * Keys the session off the prologue and nothing else, so that whether the two sides agree is
     * exactly whether they saw the same hellos. [tampered] plays the side that did not.
     */
    private class PrologueBound(private val tampered: Boolean = false) : AuthMethod {
        override val id = AuthMethodId("prologue-bound")

        override suspend fun run(io: HandshakeIo, context: AuthContext): AuthOutcome {
            io.exchange(ByteArray(0))
            val prologue = context.prologue.copyOf().also { if (tampered) it[0]++ }
            return AuthOutcome(
                sharedSecret = MessageDigest.getInstance("SHA-256").digest(prologue),
                peer = PeerIdentity(context.local.deviceId, context.local.publicKey),
            )
        }
    }

    /** Never stops talking, which is what the round limit exists for. */
    private object Endless : AuthMethod {
        override val id = AuthMethodId("endless")

        override suspend fun run(io: HandshakeIo, context: AuthContext): AuthOutcome {
            while (true) io.exchange(ByteArray(1))
        }
    }

    private companion object {
        val TIMEOUT = 5.seconds
    }
}
