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
import java.security.MessageDigest
import java.security.SecureRandom

class SasAuthMethodTest {

    @Test
    fun `both sides derive the same session key and resolve each other's identity`() = runTest {
        val (aliceIo, bobIo) = pairedIo()
        val prologue = "hello-bytes".encodeToByteArray()
        val alice = identity("alice")
        val bob = identity("bob")

        val aliceOutcome = runSide(
            method = SasAuthMethod(X25519CryptoProvider, null),
            io = aliceIo,
            role = CryptoProvider.Role.Initiator,
            prologue = prologue,
            local = alice
        )
        val bobOutcome = runSide(
            method = SasAuthMethod(X25519CryptoProvider, null),
            io = bobIo,
            role = CryptoProvider.Role.Responder,
            prologue = prologue,
            local = bob
        )

        val (aliceResult, bobResult) = aliceOutcome.await() to bobOutcome.await()

        assertArrayEquals(
            aliceResult.getOrThrow().sharedSecret,
            bobResult.getOrThrow().sharedSecret
        )
        assertEquals(bob.deviceId, aliceResult.getOrThrow().peer.deviceId)
        assertEquals(alice.deviceId, bobResult.getOrThrow().peer.deviceId)
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
            local = identity("alice"),
        )
        val bobOutcome = runSide(
            method = SasAuthMethod(
                X25519CryptoProvider,
                { _, code -> bobSas = code; PeerAuthenticator.Decision.Trust }),
            io = bobIo,
            role = CryptoProvider.Role.Responder,
            prologue = prologue,
            local = identity("bob"),
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
            local = identity("alice"),
        )
        val bobOutcome = runSide(
            method = SasAuthMethod(X25519CryptoProvider, null, confirmationCodeLength = 6),
            io = bobIo,
            role = CryptoProvider.Role.Responder,
            prologue = prologue,
            local = identity("bob"),
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
            method = SasAuthMethod(X25519CryptoProvider, null),
            io = aliceIo,
            role = CryptoProvider.Role.Initiator,
            prologue = prologue,
            local = identity("alice"),
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
    fun `an active MITM can steal both identities but cannot make the two SAS codes agree`() =
        runTest {
            // Mallory sits between Alice and Bob as two separate connections - she cannot break DH,
            // so each leg runs its own key exchange under her own ephemeral key. What she *can* do is
            // lie about whose identity that key belongs to: towards Alice she claims Bob's identity
            // fields, towards Bob she claims Alice's. Both handshakes complete and both victims end up
            // believing they reached each other directly - identity alone does not catch this.
            val (aliceIo, malloryTowardAliceIo) = pairedIo()
            val (malloryTowardBobIo, bobIo) = pairedIo()
            val prologue = "hello-bytes".encodeToByteArray()

            val alice = identity("alice")
            val bob = identity("bob")

            var aliceSas: String? = null
            var bobSas: String? = null

            val aliceOutcome = runSide(
                method = SasAuthMethod(
                    X25519CryptoProvider,
                    { _, code -> aliceSas = code; PeerAuthenticator.Decision.Trust }),
                io = aliceIo,
                role = CryptoProvider.Role.Initiator,
                prologue = prologue,
                local = alice,
            )
            val malloryTowardAlice = runSide(
                method = SasAuthMethod(X25519CryptoProvider, null),
                io = malloryTowardAliceIo,
                role = CryptoProvider.Role.Responder,
                prologue = prologue,
                local = bob.copy(), // Mallory claiming to be Bob
            )
            val malloryTowardBob = runSide(
                method = SasAuthMethod(X25519CryptoProvider, null),
                io = malloryTowardBobIo,
                role = CryptoProvider.Role.Initiator,
                prologue = prologue,
                local = alice.copy(), // Mallory claiming to be Alice
            )
            val bobOutcome = runSide(
                method = SasAuthMethod(
                    crypto = X25519CryptoProvider,
                    authenticator = { _, code -> bobSas = code; PeerAuthenticator.Decision.Trust }
                ),
                io = bobIo,
                role = CryptoProvider.Role.Responder,
                prologue = prologue,
                local = bob,
            )

            val aliceResult = aliceOutcome.await().getOrThrow()
            malloryTowardAlice.await().getOrThrow()
            malloryTowardBob.await().getOrThrow()
            val bobResult = bobOutcome.await().getOrThrow()

            // The identity layer is fully fooled: both victims believe they finished the handshake
            // with each other, not with Mallory.
            assertEquals(bob.deviceId, aliceResult.peer.deviceId)
            assertEquals(alice.deviceId, bobResult.peer.deviceId)
            // What Mallory cannot forge: her two independent DH exchanges never share a transcript, so
            // the codes the two victims would read out to each other do not match. This is the one
            // thing standing between "handshake completed" and "handshake completed with an attacker".
            // Note Mallory here is two honest runs; adaptive attacks need hand-written adversaries.
            assertNotEquals(aliceSas, bobSas)
        }

    @Test
    fun `aborts when the two sides do not agree on the prologue`() = runTest {
        val (aliceIo, bobIo) = pairedIo()

        val aliceOutcome = runSide(
            method = SasAuthMethod(X25519CryptoProvider, null),
            io = aliceIo,
            role = CryptoProvider.Role.Initiator,
            prologue = "alice-saw-this".encodeToByteArray(),
            local = identity("alice"),
        )
        val bobOutcome = runSide(
            method = SasAuthMethod(X25519CryptoProvider, null),
            io = bobIo,
            role = CryptoProvider.Role.Responder,
            prologue = "bob-saw-this".encodeToByteArray(),
            local = identity("bob"),
        )

        val results = listOf(aliceOutcome.await(), bobOutcome.await())
        assertTrue(results.all { it.isFailure })
        assertTrue(results.all { it.exceptionOrNull() is NetworkException.AuthenticationRejected })
    }

    @Test
    fun `authenticator rejection aborts the handshake with its reason`() = runTest {
        val (aliceIo, bobIo) = pairedIo()
        val prologue = "hello-bytes".encodeToByteArray()

        // Bob trusts and then waits for a confirmation that never comes - cancelled at the end.
        val bob = launch {
            runCatching {
                SasAuthMethod(X25519CryptoProvider, null).run(
                    io = bobIo,
                    context = AuthContext(
                        role = CryptoProvider.Role.Responder,
                        prologue = prologue,
                        confirmationCode = null,
                        local = identity("bob"),
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
            local = identity("alice"),
        ).await()

        assertTrue(aliceOutcome.isFailure)
        val failure = aliceOutcome.exceptionOrNull()
        assertTrue(failure is NetworkException.AuthenticationRejected)
        assertTrue(failure!!.message!!.contains("codes did not match"))
        bob.cancel()
    }

    @Test
    fun `identity is never visible on the wire in plaintext`() = runTest {
        val (aliceIo, bobIo) = pairedIo()
        val prologue = "hello-bytes".encodeToByteArray()
        val canary = "alice-identity-plaintext-canary"

        val sent = mutableListOf<ByteArray>()

        val aliceOutcome = runSide(
            method = SasAuthMethod(X25519CryptoProvider, null),
            io = RecordingIo(aliceIo, sent),
            role = CryptoProvider.Role.Initiator,
            prologue = prologue,
            local = identity(canary),
        )
        val bobOutcome = runSide(
            method = SasAuthMethod(X25519CryptoProvider, null),
            io = bobIo,
            role = CryptoProvider.Role.Responder,
            prologue = prologue,
            local = identity("bob"),
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
            method = SasAuthMethod(X25519CryptoProvider, null),
            io = RecordingIo(aliceIo, aliceSent),
            role = CryptoProvider.Role.Initiator,
            prologue = prologue,
            local = identity("alice"),
        )
        val bobOutcome = runSide(
            method = SasAuthMethod(X25519CryptoProvider, null),
            io = bobIo,
            role = CryptoProvider.Role.Responder,
            prologue = prologue,
            local = identity("bob"),
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
        local: LocalIdentity,
    ): Deferred<Result<AuthOutcome>> = async {
        runCatching {
            method.run(
                io = io,
                context = AuthContext(
                    role = role,
                    prologue = prologue,
                    confirmationCode = null,
                    local = local
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

        io.exchange(sha256(committedReveal, prologue))

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
            adversaryIo.exchange(sha256(reveal, prologue))
            adversaryIo.send(reveal)
            adversaryIo.receive()
        }

        val aliceOutcome = runSide(
            method = SasAuthMethod(X25519CryptoProvider, null),
            io = aliceIo,
            role = CryptoProvider.Role.Initiator,
            prologue = prologue,
            local = identity("alice"),
        ).await()

        assertTrue(aliceOutcome.isFailure)
        assertTrue(aliceOutcome.exceptionOrNull() is NetworkException.Protocol)
        adversary.cancel()
    }

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

    private fun identity(name: String): LocalIdentity = LocalIdentity(
        deviceId = name,
        displayName = name,
        publicKey = ByteArray(32).also { SecureRandom().nextBytes(it) },
    )

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
