package com.fserver.net.handshake

import com.fserver.net.NetLogger
import com.fserver.net.NetworkException
import com.fserver.net.dictionary.MessageDictionary
import com.fserver.net.security.CryptoProvider
import com.fserver.net.security.EphemeralIdentityStore
import com.fserver.net.security.PassthroughCryptoProvider
import com.fserver.net.security.PeerAuthenticator
import com.fserver.net.session.SessionLink
import com.fserver.net.spi.Transport
import com.fserver.net.spi.TransportCapabilities
import com.fserver.net.support.TestDictionary
import com.fserver.net.support.TestMessage
import com.fserver.net.support.channelPair
import com.fserver.net.wire.Envelope
import com.fserver.net.wire.FrameKind
import com.fserver.net.wire.ProtocolVersions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
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
        val (initiator, responder) = handshake(
            initiator = negotiator("alice", versions = 1..2),
            responder = negotiator("bob", versions = 1..1),
        )

        assertEquals(1, initiator.getOrThrow().negotiated.protocolVersion)
        assertEquals(1, responder.getOrThrow().negotiated.protocolVersion)
    }

    @Test
    fun `no shared version fails on both ends, and the peer is told why`() = runBlocking {
        val (initiator, responder) = handshake(
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
        val (initiator, responder) = handshake(
            initiatorCapabilities = TransportCapabilities(maxFrameSize = 64 * 1024),
            responderCapabilities = TransportCapabilities(maxFrameSize = 16 * 1024),
        )

        assertEquals(16 * 1024, initiator.getOrThrow().negotiated.maxFrameSize)
        assertEquals(16 * 1024, responder.getOrThrow().negotiated.maxFrameSize)
    }

    @Test
    fun `the dictionary's verdict, not the network's, sets the effective version`() = runBlocking {
        val (initiator, responder) = handshake(
            initiator = negotiator("alice", dictionary = TestDictionary(version = 2, supported = 1..2)),
            responder = negotiator("bob", dictionary = TestDictionary(version = 1, supported = 1..2)),
        )

        assertEquals(1, initiator.getOrThrow().negotiated.dictionaryVersion)
        assertEquals(1, responder.getOrThrow().negotiated.dictionaryVersion)
    }

    @Test
    fun `a dictionary version the peer cannot serve is refused with the descriptor it saw`() =
        runBlocking {
            val (initiator, responder) = handshake(
                initiator = negotiator("alice", dictionary = TestDictionary(version = 7, supported = 7..7)),
                responder = negotiator("bob", dictionary = TestDictionary(version = 1, supported = 1..1)),
            )

            val refused = responder.exceptionOrNull()
            assertTrue(refused is NetworkException.DictionaryMismatch)
            assertEquals(7, (refused as NetworkException.DictionaryMismatch).remote.version)
            assertTrue(initiator.exceptionOrNull() is NetworkException.Handshake)
        }

    @Test
    fun `a peer the authenticator refuses is told so, and neither end gets a link`() = runBlocking {
        val (initiator, responder) = handshake(
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
    fun `the authenticator sees the identity and the transport's confirmation code`() =
        runBlocking {
            var seenCode: String? = null
            var seenDeviceId: String? = null
            val alice = EphemeralIdentityStore(displayName = "alice")

            handshake(
                initiator = negotiator("alice", identityStore = alice),
                responder = negotiator("bob", authenticator = { candidate, code ->
                    seenDeviceId = candidate.deviceId
                    seenCode = code
                    PeerAuthenticator.Decision.Trust
                }),
                responderConfirmationCode = "4821",
            )

            assertEquals(alice.local.deviceId, seenDeviceId)
            assertEquals("4821", seenCode)
        }

    @Test
    fun `a frame of the wrong kind ends the handshake instead of being read as a hello`() =
        runBlocking {
            val (ours, theirs) = channelPair()
            val pump = FramePump(scope, ours)

            theirs.send(
                Envelope.Codec.encode(
                    Envelope(ProtocolVersions.CURRENT, FrameKind.READY, messageId = 0)
                )
            )

            val outcome = runCatching {
                withTimeout(TIMEOUT) {
                    negotiator("bob").negotiate(
                        pump = pump,
                        role = CryptoProvider.Role.Responder,
                        capabilities = TransportCapabilities(),
                        confirmationCode = null,
                        timeout = TIMEOUT,
                    )
                }
            }

            val error = outcome.exceptionOrNull()
            assertTrue(error is NetworkException.Handshake)
            assertTrue(error!!.message!!.contains("expected HELLO"))
        }

    @Test
    fun `a peer that never answers fails the handshake on the timeout, not on the link`() =
        runBlocking {
            val (ours, _) = channelPair()
            val pump = FramePump(scope, ours)

            val outcome = runCatching {
                negotiator("bob").negotiate(
                    pump = pump,
                    role = CryptoProvider.Role.Responder,
                    capabilities = TransportCapabilities(),
                    confirmationCode = null,
                    timeout = 100.milliseconds,
                )
            }

            assertTrue(outcome.exceptionOrNull() is NetworkException.Handshake)
        }

    // ------------------------------------------------------------------ helpers

    private fun negotiator(
        name: String,
        dictionary: MessageDictionary<TestMessage> = TestDictionary(),
        versions: IntRange = ProtocolVersions.SUPPORTED,
        authenticator: PeerAuthenticator? = null,
        identityStore: EphemeralIdentityStore = EphemeralIdentityStore(displayName = name),
        crypto: CryptoProvider = PassthroughCryptoProvider,
    ) = HandshakeNegotiator(
        identityStore = identityStore,
        dictionary = dictionary,
        crypto = crypto,
        authenticator = authenticator,
        protocolVersions = versions,
        logger = NetLogger.None,
    )

    /** Runs both halves at once and hands back what each of them concluded. */
    private suspend fun handshake(
        initiator: HandshakeNegotiator<TestMessage> = negotiator("alice"),
        responder: HandshakeNegotiator<TestMessage> = negotiator("bob"),
        initiatorCapabilities: TransportCapabilities = TransportCapabilities(),
        responderCapabilities: TransportCapabilities = TransportCapabilities(),
        responderConfirmationCode: String? = null,
    ): Pair<Result<SessionLink>, Result<SessionLink>> {
        val (initiatorChannel, responderChannel) = channelPair()

        val initiatorSide = start(
            initiator,
            initiatorChannel,
            CryptoProvider.Role.Initiator,
            initiatorCapabilities,
            confirmationCode = null,
        )
        val responderSide = start(
            responder,
            responderChannel,
            CryptoProvider.Role.Responder,
            responderCapabilities,
            confirmationCode = responderConfirmationCode,
        )

        return withTimeout(TIMEOUT) { initiatorSide.await() to responderSide.await() }
    }

    private fun start(
        negotiator: HandshakeNegotiator<TestMessage>,
        channel: Transport.Channel,
        role: CryptoProvider.Role,
        capabilities: TransportCapabilities,
        confirmationCode: String?,
    ): Deferred<Result<SessionLink>> = scope.async {
        val pump = FramePump(scope, channel)
        runCatching {
            negotiator.negotiate(
                pump = pump,
                role = role,
                capabilities = capabilities,
                confirmationCode = confirmationCode,
                timeout = TIMEOUT,
            )
        }
    }

    private companion object {
        val TIMEOUT = 5.seconds
    }
}
