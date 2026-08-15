package com.fserver.net.security.auth

import com.fserver.net.NetworkException
import com.fserver.net.security.PeerAuthenticator
import com.fserver.net.security.crypto.CryptoProvider
import com.fserver.net.security.crypto.X25519CryptoProvider
import com.fserver.net.security.identity.LocalIdentity
import com.fserver.net.wire.ByteWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

class SasAuthMethodTest {

    private val trustAll = PeerAuthenticator { _, _ -> PeerAuthenticator.Decision.Trust }

    @Test
    fun `both sides derive the same session key and resolve each other's identity`() = runTest {
        val (aliceIo, bobIo) = pairedIo()
        val prologue = "hello-bytes".encodeToByteArray()
        val alice = TestPeer("alice")
        val bob = TestPeer("bob")

        val aliceOutcome = runSide(
            method = SasAuthMethod(X25519CryptoProvider, trustAll),
            io = aliceIo,
            role = CryptoProvider.Role.Initiator,
            prologue = prologue,
            peer = alice
        )
        val bobOutcome = runSide(
            method = SasAuthMethod(X25519CryptoProvider, trustAll),
            io = bobIo,
            role = CryptoProvider.Role.Responder,
            prologue = prologue,
            peer = bob
        )

        val (aliceResult, bobResult) = aliceOutcome.await() to bobOutcome.await()

        assertArrayEquals(
            aliceResult.getOrThrow().sharedSecret,
            bobResult.getOrThrow().sharedSecret
        )
        assertEquals(bob.identity.deviceId, aliceResult.getOrThrow().peer.deviceId)
        assertEquals(alice.identity.deviceId, bobResult.getOrThrow().peer.deviceId)
    }

    @Test
    fun `both sides compute the same SAS code on an honest run`() = runTest {
        val (aliceIo, bobIo) = pairedIo()
        val prologue = "hello-bytes".encodeToByteArray()

        var aliceSas: String? = null
        var bobSas: String? = null

        val aliceOutcome = runSide(
            method = SasAuthMethod(
                X25519CryptoProvider,
                { _, code -> aliceSas = code; PeerAuthenticator.Decision.Trust }
            ),
            io = aliceIo,
            role = CryptoProvider.Role.Initiator,
            prologue = prologue,
            peer = TestPeer("alice"),
        )
        val bobOutcome = runSide(
            method = SasAuthMethod(
                X25519CryptoProvider,
                { _, code -> bobSas = code; PeerAuthenticator.Decision.Trust }),
            io = bobIo,
            role = CryptoProvider.Role.Responder,
            prologue = prologue,
            peer = TestPeer("bob"),
        )

        aliceOutcome.await().getOrThrow()
        bobOutcome.await().getOrThrow()

        assertTrue(!aliceSas.isNullOrEmpty())
        assertEquals(aliceSas, bobSas)
    }

    @Test
    fun `SAS code is exactly the configured number of digits`() = runTest {
        val (aliceIo, bobIo) = pairedIo()
        val prologue = "hello-bytes".encodeToByteArray()

        var aliceSas: String? = null

        val aliceOutcome = runSide(
            method = SasAuthMethod(
                X25519CryptoProvider,
                { _, code -> aliceSas = code; PeerAuthenticator.Decision.Trust },
                confirmationCodeLength = 6,
            ),
            io = aliceIo,
            role = CryptoProvider.Role.Initiator,
            prologue = prologue,
            peer = TestPeer("alice"),
        )
        val bobOutcome = runSide(
            method = SasAuthMethod(X25519CryptoProvider, trustAll, confirmationCodeLength = 6),
            io = bobIo,
            role = CryptoProvider.Role.Responder,
            prologue = prologue,
            peer = TestPeer("bob"),
        )

        aliceOutcome.await().getOrThrow()
        bobOutcome.await().getOrThrow()

        assertTrue("expected 6 digits, got '$aliceSas'", aliceSas!!.matches(Regex("\\d{6}")))
    }

    @Test
    fun `rejects a peer that reveals a key it never committed to`() = runTest {
        val (aliceIo, adversaryIo) = pairedIo()
        val prologue = "hello-bytes".encodeToByteArray()

        val adversary = launch { keySubstitutingAdversary(adversaryIo, prologue) }

        val aliceOutcome = runSide(
            method = SasAuthMethod(X25519CryptoProvider, trustAll),
            io = aliceIo,
            role = CryptoProvider.Role.Initiator,
            prologue = prologue,
            peer = TestPeer("alice"),
        ).await()

        assertTrue(aliceOutcome.isFailure)
        assertTrue(aliceOutcome.exceptionOrNull() is NetworkException.AuthenticationRejected)
        adversary.cancel()
    }

    @Test
    fun `rejects a reveal with an oversized key even when its commitment matches`() = runTest {
        // The commitment covers the exact reveal bytes, so it verifies - the strict decode is the
        // only thing preventing a committed blob from being re-split into other (key, nonce)
        // parses after the reveal to grind the SAS.
        val reveal = ByteWriter()
            .bytes(ByteArray(33).also { SecureRandom().nextBytes(it) })
            .bytes(ByteArray(32).also { SecureRandom().nextBytes(it) })
            .toByteArray()

        assertRevealRejected(reveal)
    }

    @Test
    fun `rejects a reveal with trailing bytes even when its commitment matches`() = runTest {
        val reveal = ByteWriter()
            .bytes(X25519CryptoProvider.newKeyExchange().publicKey)
            .bytes(ByteArray(32).also { SecureRandom().nextBytes(it) })
            .raw(byteArrayOf(1))
            .toByteArray()

        assertRevealRejected(reveal)
    }

    @Test
    fun `rejects a reveal with a wrong-size nonce even when its commitment matches`() = runTest {
        val reveal = ByteWriter()
            .bytes(X25519CryptoProvider.newKeyExchange().publicKey)
            .bytes(ByteArray(31).also { SecureRandom().nextBytes(it) })
            .toByteArray()

        assertRevealRejected(reveal)
    }

    @Test
    fun `reveal is not sent until the peer's commitment has arrived`() = runTest {
        // The commitment scheme only binds if the reveal waits for the peer to commit first -
        // otherwise a peer could pick its keypair after seeing ours and grind the SAS.
        val (aliceIo, bobIo) = pairedIo()
        val prologue = "hello-bytes".encodeToByteArray()
        val events = mutableListOf<String>()

        val aliceOutcome = runSide(
            method = SasAuthMethod(X25519CryptoProvider, trustAll),
            io = object : HandshakeIo {
                override suspend fun send(payload: ByteArray) {
                    events += "send"
                    aliceIo.send(payload)
                }

                override suspend fun receive(): ByteArray {
                    events += "receive"
                    return aliceIo.receive()
                }
            },
            role = CryptoProvider.Role.Initiator,
            prologue = prologue,
            peer = TestPeer("alice"),
        )
        val bobOutcome = runSide(
            method = SasAuthMethod(X25519CryptoProvider, trustAll),
            io = bobIo,
            role = CryptoProvider.Role.Responder,
            prologue = prologue,
            peer = TestPeer("bob"),
        )

        aliceOutcome.await().getOrThrow()
        bobOutcome.await().getOrThrow()

        // Commitment out, peer commitment in, only then the reveal.
        assertEquals(listOf("send", "receive", "send"), events.take(3))
    }

    @Test
    fun `an active MITM cannot claim the victims' identities`() = runTest {
        // Before proof of possession Mallory could relay both legs and claim Bob's identity toward
        // Alice and Alice's toward Bob. Now each leg requires a transcript signature under the
        // claimed key, which she does not hold - both victims must reject her.
        val (aliceIo, malloryTowardAliceIo) = pairedIo()
        val (malloryTowardBobIo, bobIo) = pairedIo()
        val prologue = "hello-bytes".encodeToByteArray()

        val alice = TestPeer("alice")
        val bob = TestPeer("bob")
        val mallory = TestPeer("mallory")

        val aliceOutcome = runSide(
            method = SasAuthMethod(X25519CryptoProvider, trustAll),
            io = aliceIo,
            role = CryptoProvider.Role.Initiator,
            prologue = prologue,
            peer = alice,
        )
        val malloryTowardAlice = runSide(
            method = SasAuthMethod(X25519CryptoProvider, trustAll),
            io = malloryTowardAliceIo,
            role = CryptoProvider.Role.Responder,
            prologue = prologue,
            peer = mallory,
            claimed = bob.identity, // Mallory claiming to be Bob, signing with her own key
        )
        val malloryTowardBob = runSide(
            method = SasAuthMethod(X25519CryptoProvider, trustAll),
            io = malloryTowardBobIo,
            role = CryptoProvider.Role.Initiator,
            prologue = prologue,
            peer = mallory,
            claimed = alice.identity, // Mallory claiming to be Alice
        )
        val bobOutcome = runSide(
            method = SasAuthMethod(X25519CryptoProvider, trustAll),
            io = bobIo,
            role = CryptoProvider.Role.Responder,
            prologue = prologue,
            peer = bob,
        )

        val aliceResult = aliceOutcome.await()
        val bobResult = bobOutcome.await()

        assertTrue(aliceResult.isFailure)
        assertTrue(aliceResult.exceptionOrNull() is NetworkException.AuthenticationRejected)
        assertTrue(bobResult.isFailure)
        assertTrue(bobResult.exceptionOrNull() is NetworkException.AuthenticationRejected)

        malloryTowardAlice.cancel()
        malloryTowardBob.cancel()
    }

    @Test
    fun `an active MITM under her own identity cannot make the two SAS codes agree`() = runTest {
        // Proof of possession forces Mallory to show her own identity, so this variant is what
        // remains of the classic relay: both handshakes complete, but her two independent DH
        // exchanges never share a transcript, so the codes the victims would read out to each
        // other cannot match. Note Mallory is two honest runs; adaptive attacks need hand-written
        // adversaries.
        val (aliceIo, malloryTowardAliceIo) = pairedIo()
        val (malloryTowardBobIo, bobIo) = pairedIo()
        val prologue = "hello-bytes".encodeToByteArray()

        val mallory = TestPeer("mallory")

        var aliceSas: String? = null
        var bobSas: String? = null

        val aliceOutcome = runSide(
            method = SasAuthMethod(
                X25519CryptoProvider,
                { _, code -> aliceSas = code; PeerAuthenticator.Decision.Trust }),
            io = aliceIo,
            role = CryptoProvider.Role.Initiator,
            prologue = prologue,
            peer = TestPeer("alice"),
        )
        val malloryTowardAlice = runSide(
            method = SasAuthMethod(X25519CryptoProvider, trustAll),
            io = malloryTowardAliceIo,
            role = CryptoProvider.Role.Responder,
            prologue = prologue,
            peer = mallory,
        )
        val malloryTowardBob = runSide(
            method = SasAuthMethod(X25519CryptoProvider, trustAll),
            io = malloryTowardBobIo,
            role = CryptoProvider.Role.Initiator,
            prologue = prologue,
            peer = mallory,
        )
        val bobOutcome = runSide(
            method = SasAuthMethod(
                crypto = X25519CryptoProvider,
                authenticator = { _, code -> bobSas = code; PeerAuthenticator.Decision.Trust }
            ),
            io = bobIo,
            role = CryptoProvider.Role.Responder,
            prologue = prologue,
            peer = TestPeer("bob"),
        )

        val aliceResult = aliceOutcome.await().getOrThrow()
        malloryTowardAlice.await().getOrThrow()
        malloryTowardBob.await().getOrThrow()
        val bobResult = bobOutcome.await().getOrThrow()

        // Both victims see Mallory's identity - she can no longer hide it -
        // and the SAS codes still refuse to line up.
        assertEquals(mallory.identity.deviceId, aliceResult.peer.deviceId)
        assertEquals(mallory.identity.deviceId, bobResult.peer.deviceId)
        assertNotEquals(aliceSas, bobSas)
    }

    @Test
    fun `rejects a peer that claims an identity key it cannot sign for`() = runTest {
        val (aliceIo, bobIo) = pairedIo()
        val prologue = "hello-bytes".encodeToByteArray()
        val bob = TestPeer("bob")

        val imposter = runSide(
            method = SasAuthMethod(X25519CryptoProvider, trustAll),
            io = bobIo,
            role = CryptoProvider.Role.Responder,
            prologue = prologue,
            peer = TestPeer("imposter"),
            claimed = bob.identity,
        )

        val aliceOutcome = runSide(
            method = SasAuthMethod(X25519CryptoProvider, trustAll),
            io = aliceIo,
            role = CryptoProvider.Role.Initiator,
            prologue = prologue,
            peer = TestPeer("alice"),
        ).await()

        assertTrue(aliceOutcome.isFailure)
        assertTrue(aliceOutcome.exceptionOrNull() is NetworkException.AuthenticationRejected)
        imposter.cancel()
    }

    @Test
    fun `responder withholds its identity until the initiator proves one`() = runTest {
        // Active device enumeration defense: a responder must not reveal who it is to an
        // initiator that completed the key exchange but never authenticated.
        val (adversaryIo, responderIo) = pairedIo()
        val prologue = "hello-bytes".encodeToByteArray()
        val responderSent = mutableListOf<ByteArray>()

        val adversary = launch {
            val reveal = ByteWriter()
                .bytes(X25519CryptoProvider.newKeyExchange().publicKey)
                .bytes(ByteArray(32).also { SecureRandom().nextBytes(it) })
                .toByteArray()
            adversaryIo.exchange(commitment(reveal, prologue))
            adversaryIo.send(reveal)
            adversaryIo.receive()
            adversaryIo.send(ByteArray(64)) // garbage instead of a sealed identity
        }

        val responderOutcome = runSide(
            method = SasAuthMethod(X25519CryptoProvider, trustAll),
            io = RecordingIo(responderIo, responderSent),
            role = CryptoProvider.Role.Responder,
            prologue = prologue,
            peer = TestPeer("responder"),
        ).await()

        assertTrue(responderOutcome.isFailure)
        // Commitment and reveal only - the sealed identity frame was never sent.
        assertEquals(2, responderSent.size)
        adversary.cancel()
    }

    @Test
    fun `construction fails without an authenticator`() {
        assertTrue(
            runCatching {
                SasAuthMethod(X25519CryptoProvider, null)
            }.exceptionOrNull() is IllegalArgumentException
        )
    }

    @Test
    fun `construction fails on a degenerate confirmation code length`() {
        for (length in intArrayOf(-1, 0, 3, 17)) {
            assertTrue(
                "length $length must be rejected",
                runCatching {
                    SasAuthMethod(X25519CryptoProvider, trustAll, confirmationCodeLength = length)
                }.exceptionOrNull() is IllegalArgumentException
            )
        }
    }

    @Test
    fun `mirrored frames do not authenticate`() = runTest {
        // An adversary that echoes every frame back: the commitment and reveal mirror cleanly, but
        // the directional AEAD keys differ per side, so the reflected identity frame cannot open -
        // and even if it could, the role byte inside the signed data would not verify.
        val (aliceIo, mirrorIo) = pairedIo()
        val prologue = "hello-bytes".encodeToByteArray()

        val mirror = launch {
            while (true) {
                mirrorIo.send(mirrorIo.receive())
            }
        }

        val aliceOutcome = runSide(
            method = SasAuthMethod(X25519CryptoProvider, trustAll),
            io = aliceIo,
            role = CryptoProvider.Role.Initiator,
            prologue = prologue,
            peer = TestPeer("alice"),
        ).await()

        assertTrue(aliceOutcome.isFailure)
        assertTrue(aliceOutcome.exceptionOrNull() is NetworkException)
        mirror.cancel()
    }

    @Test
    fun `aborts when the two sides do not agree on the prologue`() = runTest {
        val (aliceIo, bobIo) = pairedIo()

        val aliceOutcome = runSide(
            method = SasAuthMethod(X25519CryptoProvider, trustAll),
            io = aliceIo,
            role = CryptoProvider.Role.Initiator,
            prologue = "alice-saw-this".encodeToByteArray(),
            peer = TestPeer("alice"),
        )
        val bobOutcome = runSide(
            method = SasAuthMethod(X25519CryptoProvider, trustAll),
            io = bobIo,
            role = CryptoProvider.Role.Responder,
            prologue = "bob-saw-this".encodeToByteArray(),
            peer = TestPeer("bob"),
        )

        val results = listOf(aliceOutcome.await(), bobOutcome.await())
        assertTrue(results.all { it.isFailure })
        assertTrue(results.all { it.exceptionOrNull() is NetworkException.AuthenticationRejected })
    }

    @Test
    fun `authenticator rejection aborts the handshake with its reason`() = runTest {
        val (aliceIo, bobIo) = pairedIo()
        val prologue = "hello-bytes".encodeToByteArray()
        val bob = TestPeer("bob")

        // Bob trusts and then waits for a confirmation that never comes - cancelled at the end.
        val bobJob = launch {
            runCatching {
                SasAuthMethod(X25519CryptoProvider, trustAll).run(
                    io = bobIo,
                    context = AuthContext(
                        role = CryptoProvider.Role.Responder,
                        prologue = prologue,
                        confirmationCode = null,
                        local = bob.identity,
                        sign = { bob.sign(it) },
                    )
                )
            }
        }

        val aliceOutcome = runSide(
            method = SasAuthMethod(
                X25519CryptoProvider,
                { _, _ -> PeerAuthenticator.Decision.Reject("codes did not match") }),
            io = aliceIo,
            role = CryptoProvider.Role.Initiator,
            prologue = prologue,
            peer = TestPeer("alice"),
        ).await()

        assertTrue(aliceOutcome.isFailure)
        val failure = aliceOutcome.exceptionOrNull()
        assertTrue(failure is NetworkException.AuthenticationRejected)
        assertTrue(failure!!.message!!.contains("codes did not match"))
        bobJob.cancel()
    }

    @Test
    fun `identity is never visible on the wire in plaintext`() = runTest {
        val (aliceIo, bobIo) = pairedIo()
        val prologue = "hello-bytes".encodeToByteArray()
        val canary = "alice-identity-plaintext-canary"

        val sent = mutableListOf<ByteArray>()

        val aliceOutcome = runSide(
            method = SasAuthMethod(X25519CryptoProvider, trustAll),
            io = RecordingIo(aliceIo, sent),
            role = CryptoProvider.Role.Initiator,
            prologue = prologue,
            peer = TestPeer(canary),
        )
        val bobOutcome = runSide(
            method = SasAuthMethod(X25519CryptoProvider, trustAll),
            io = bobIo,
            role = CryptoProvider.Role.Responder,
            prologue = prologue,
            peer = TestPeer("bob"),
        )

        aliceOutcome.await().getOrThrow()
        bobOutcome.await().getOrThrow()

        val needle = canary.encodeToByteArray()
        sent.forEachIndexed { index, frame ->
            assertFalse("frame $index carries the identity in plaintext", frame.containsSlice(needle))
        }
    }

    @Test
    fun `session channel cannot open handshake frames`() = runTest {
        // The session AEAD restarts nonce counters at zero. If the handshake sealed its frames
        // under the same key, the first session frames would reuse (key, nonce) pairs - so the
        // session key being able to open a handshake frame is a security defect.
        val (aliceIo, bobIo) = pairedIo()
        val prologue = "hello-bytes".encodeToByteArray()

        val aliceSent = mutableListOf<ByteArray>()

        val aliceOutcome = runSide(
            method = SasAuthMethod(X25519CryptoProvider, trustAll),
            io = RecordingIo(aliceIo, aliceSent),
            role = CryptoProvider.Role.Initiator,
            prologue = prologue,
            peer = TestPeer("alice"),
        )
        val bobOutcome = runSide(
            method = SasAuthMethod(X25519CryptoProvider, trustAll),
            io = bobIo,
            role = CryptoProvider.Role.Responder,
            prologue = prologue,
            peer = TestPeer("bob"),
        )

        aliceOutcome.await().getOrThrow()
        val bobResult = bobOutcome.await().getOrThrow()

        // Frames from Alice: [0] commitment, [1] reveal, [2] sealed identity, [3] sealed confirm.
        val sealedIdentity = aliceSent[2]
        val bobSessionAead =
            X25519CryptoProvider.aead(bobResult.sharedSecret, CryptoProvider.Role.Responder)

        assertTrue(runCatching { bobSessionAead.open(sealedIdentity) }.isFailure)
    }

    private fun CoroutineScope.runSide(
        method: SasAuthMethod,
        io: HandshakeIo,
        role: CryptoProvider.Role,
        prologue: ByteArray,
        peer: TestPeer,
        claimed: LocalIdentity = peer.identity,
    ): Deferred<Result<AuthOutcome>> = async {
        runCatching {
            method.run(
                io = io,
                context = AuthContext(
                    role = role,
                    prologue = prologue,
                    confirmationCode = null,
                    local = claimed,
                    sign = { peer.sign(it) },
                )
            )
        }
    }

    /**
     * Commits to one key, then reveals a different one - the substitution the commit-then-reveal
     * shape exists to catch. A [SasAuthMethod] on the other end must reject this, not just end up
     * with a mismatched SAS.
     */
    private suspend fun keySubstitutingAdversary(io: HandshakeIo, prologue: ByteArray) {
        val nonce = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val committedReveal = ByteWriter()
            .bytes(X25519CryptoProvider.newKeyExchange().publicKey)
            .bytes(nonce)
            .toByteArray()

        io.exchange(commitment(committedReveal, prologue))

        val substituteReveal = ByteWriter()
            .bytes(X25519CryptoProvider.newKeyExchange().publicKey)
            .bytes(nonce)
            .toByteArray()
        io.send(substituteReveal)
        io.receive()
    }

    /** Commits honestly to [reveal] and asserts the peer rejects the reveal itself as malformed. */
    private suspend fun TestScope.assertRevealRejected(reveal: ByteArray) {
        val (aliceIo, adversaryIo) = pairedIo()
        val prologue = "hello-bytes".encodeToByteArray()

        val adversary = launch {
            adversaryIo.exchange(commitment(reveal, prologue))
            adversaryIo.send(reveal)
            adversaryIo.receive()
        }

        val aliceOutcome = runSide(
            method = SasAuthMethod(X25519CryptoProvider, trustAll),
            io = aliceIo,
            role = CryptoProvider.Role.Initiator,
            prologue = prologue,
            peer = TestPeer("alice"),
        ).await()

        assertTrue(aliceOutcome.isFailure)
        assertTrue(aliceOutcome.exceptionOrNull() is NetworkException.Protocol)
        adversary.cancel()
    }

    /** Mirrors the production commitment: labelled SHA-256 over the reveal and prologue. */
    private fun commitment(reveal: ByteArray, prologue: ByteArray): ByteArray =
        sha256("sas-1:commitment".encodeToByteArray(), reveal, prologue)

    private fun sha256(vararg parts: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").run {
            parts.forEach(::update)
            digest()
        }

    private fun ByteArray.containsSlice(needle: ByteArray): Boolean {
        if (needle.isEmpty() || size < needle.size) return false
        outer@ for (start in 0..size - needle.size) {
            for (offset in needle.indices) {
                if (this[start + offset] != needle[offset]) continue@outer
            }
            return true
        }
        return false
    }

    /** A device with a real P-256 identity pair, signing the way a KeyStore-backed store would. */
    private class TestPeer(name: String) {
        private val keys = KeyPairGenerator.getInstance("EC")
            .apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair()

        val identity = LocalIdentity(
            deviceId = name,
            displayName = name,
            publicKey = (keys.public as ECPublicKey).uncompressedPoint(),
        )

        fun sign(data: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run {
            initSign(keys.private)
            update(data)
            sign()
        }

        private fun ECPublicKey.uncompressedPoint(): ByteArray =
            byteArrayOf(0x04) + w.affineX.toByteArray().fitTo(32) + w.affineY.toByteArray().fitTo(32)

        private fun ByteArray.fitTo(length: Int): ByteArray = when {
            size == length -> this
            size > length -> copyOfRange(size - length, size)
            else -> ByteArray(length - size) + this
        }
    }

    private fun pairedIo(): Pair<HandshakeIo, HandshakeIo> {
        val aToB = Channel<ByteArray>(Channel.UNLIMITED)
        val bToA = Channel<ByteArray>(Channel.UNLIMITED)
        return PairedIo(aToB, bToA) to PairedIo(bToA, aToB)
    }

    private class PairedIo(
        private val outgoing: Channel<ByteArray>,
        private val incoming: Channel<ByteArray>,
    ) : HandshakeIo {
        override suspend fun send(payload: ByteArray) {
            outgoing.send(payload)
        }

        override suspend fun receive(): ByteArray = incoming.receive()
    }

    private class RecordingIo(
        private val inner: HandshakeIo,
        private val sent: MutableList<ByteArray>,
    ) : HandshakeIo {
        override suspend fun send(payload: ByteArray) {
            sent += payload
            inner.send(payload)
        }

        override suspend fun receive(): ByteArray = inner.receive()
    }
}
