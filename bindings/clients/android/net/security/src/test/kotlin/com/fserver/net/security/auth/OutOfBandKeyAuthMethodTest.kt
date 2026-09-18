package com.fserver.net.security.auth

import com.fserver.common.exception.NetworkException
import com.fserver.common.model.Fingerprint
import com.fserver.net.security.auth.oob.OutOfBandKeyAuthMethod
import com.fserver.net.security.crypto.CryptoProvider
import com.fserver.net.security.crypto.IdentitySignature
import com.fserver.net.security.crypto.X25519CryptoProvider
import com.fserver.net.security.identity.IdentityStore
import com.fserver.net.security.identity.LocalIdentity
import com.fserver.net.security.identity.PeerIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

/**
 * Pairing off a scanned fingerprint. The method reaches a key and states which device it expected;
 * checking the proven identity against that is the handshake's job, so these tests do it the way
 * `AuthPhase` does.
 */
class OutOfBandKeyAuthMethodTest {

    private val trustAll: TrustCheck = { _, _ -> }

    @Test
    fun `both sides derive the same session key and resolve each other's identity`() = runTest {
        val (aliceIo, bobIo) = pairedIo()
        val alice = TestPeer("alice")
        val bob = TestPeer("bob")

        val aliceOutcome = runSide(aliceIo, CryptoProvider.Role.Initiator, alice, scanned = bob)
        val bobOutcome = runSide(bobIo, CryptoProvider.Role.Responder, bob)

        val aliceResult = aliceOutcome.await().getOrThrow()
        val bobResult = bobOutcome.await().getOrThrow()

        assertArrayEquals(aliceResult.sharedSecret, bobResult.sharedSecret)
        assertEquals(bob.identity.deviceId, aliceResult.peer.deviceId)
        assertEquals(alice.identity.deviceId, bobResult.peer.deviceId)
    }

    @Test
    fun `the scanned device is what has to answer, however honest whoever did is`() = runTest {
        // A perfectly honest device that simply is not the one whose code was scanned. Nothing is
        // wrong with its proof - it is the wrong device, and only the fingerprint says so.
        val (aliceIo, bobIo) = pairedIo()
        val scanned = TestPeer("the-device-in-my-hand")

        val aliceOutcome =
            runSide(aliceIo, CryptoProvider.Role.Initiator, TestPeer("alice"), scanned = scanned)
        val bobOutcome = runSide(bobIo, CryptoProvider.Role.Responder, TestPeer("bob"))

        val failure = aliceOutcome.await().exceptionOrNull()
        assertTrue(failure is NetworkException.AuthenticationRejected)
        bobOutcome.cancel()
    }

    @Test
    fun `a peer claiming a key it cannot sign for is rejected`() = runTest {
        // The scanned fingerprint is public by construction - it was on a screen, and it names a
        // key that every completed handshake states. Claiming that identity is the whole attack,
        // and the transcript signature is what stops it.
        val (aliceIo, malloryIo) = pairedIo()
        val victim = TestPeer("bob")
        val mallory = TestPeer("mallory")

        val aliceOutcome =
            runSide(aliceIo, CryptoProvider.Role.Initiator, TestPeer("alice"), scanned = victim)
        val malloryOutcome = runSide(
            io = malloryIo,
            role = CryptoProvider.Role.Responder,
            peer = mallory,
            claimed = victim.identity,
        )

        assertTrue(aliceOutcome.await().isFailure)
        malloryOutcome.cancel()
    }

    @Test
    fun `an initiator with nothing scanned refuses to run`() = runTest {
        val (aliceIo, _) = pairedIo()

        val failure = runCatching {
            OutOfBandKeyAuthMethod(X25519CryptoProvider).run(
                io = aliceIo,
                context = context(CryptoProvider.Role.Initiator, TestPeer("alice").identity, null),
            )
        }.exceptionOrNull()

        // Without it this is a bare key agreement wearing the name of a pairing method.
        assertTrue(failure is NetworkException.AuthenticationRejected)
    }

    @Test
    fun `a disagreement about the prologue ends in a refusal, not a session`() = runTest {
        // Nothing here checks the prologue on its own: it is folded into the key, so a side that
        // saw a different hello simply cannot open what the other one sealed. The responder opens
        // first and is the one that says no; the initiator is left waiting, which on a real link
        // is the CLOSE frame and the auth deadline.
        val (aliceIo, bobIo) = pairedIo()
        val bob = TestPeer("bob")

        val aliceOutcome = runSide(
            io = aliceIo,
            role = CryptoProvider.Role.Initiator,
            peer = TestPeer("alice"),
            scanned = bob,
            prologue = "alice-saw-this".encodeToByteArray(),
        )
        val bobOutcome = runSide(
            io = bobIo,
            role = CryptoProvider.Role.Responder,
            peer = bob,
            prologue = "bob-saw-this".encodeToByteArray(),
        )

        val failure = bobOutcome.await().exceptionOrNull()
        assertTrue(failure is NetworkException.AuthenticationRejected)
        aliceOutcome.cancel()
    }

    @Test
    fun `each side learns whether the other still has it pinned`() = runTest {
        val (aliceIo, bobIo) = pairedIo()
        val bob = TestPeer("bob")

        // The scanned side is meeting this caller for the first time; the caller came from a QR.
        val aliceOutcome =
            runSide(aliceIo, CryptoProvider.Role.Initiator, TestPeer("alice"), scanned = bob)
        val bobOutcome = runSide(bobIo, CryptoProvider.Role.Responder, bob, knowsPeer = false)

        assertFalse(aliceOutcome.await().getOrThrow().peerKnowsUs)
        assertTrue(bobOutcome.await().getOrThrow().peerKnowsUs)
    }

    /**
     * One side of a handshake: the method, then what the handshake itself does with what the
     * method reached - prove the identities, let the method say whether this is the device it was
     * pointed at, trade trust hints, gate the peer, run the confirmation.
     */
    private fun CoroutineScope.runSide(
        io: HandshakeIo,
        role: CryptoProvider.Role,
        peer: TestPeer,
        scanned: TestPeer? = null,
        claimed: LocalIdentity = peer.identity,
        prologue: ByteArray = PROLOGUE,
        knowsPeer: Boolean = true,
        trust: TrustCheck = trustAll,
    ): Deferred<Result<SideResult>> = async {
        runCatching {
            val context = context(role, claimed, scanned?.identity?.fingerprint, prologue)
            val outcome = OutOfBandKeyAuthMethod(X25519CryptoProvider).run(io, context)
            val proven = IdentityExchange.run(io, context, outcome, peer.store(claimed))

            outcome.verifyPeer(proven)

            val peerKnowsUs = KnownPeerExchange.run(io, outcome.aead, knowsPeer)

            trust(proven, outcome.confirmationCode)
            outcome.confirm()

            SideResult(outcome.sharedSecret(), proven, peerKnowsUs)
        }
    }

    private class SideResult(
        val sharedSecret: ByteArray,
        val peer: PeerIdentity,
        val peerKnowsUs: Boolean,
    )

    private fun context(
        role: CryptoProvider.Role,
        claimed: LocalIdentity,
        scanned: Fingerprint?,
        prologue: ByteArray = PROLOGUE,
    ) = AuthContext(
        role = role,
        request = scanned?.let { AuthRequest(params = OutOfBandKeyAuthMethod.Params(it)) },
        prologue = prologue,
        confirmationCode = null,
        local = claimed,
    )

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

        /** Signs with this device's key while stating whatever [claimed] says it is. */
        fun store(claimed: LocalIdentity = identity): IdentityStore = object : IdentityStore {
            override suspend fun local(): LocalIdentity = claimed

            override suspend fun sign(data: ByteArray): ByteArray = this@TestPeer.sign(data)

            override suspend fun verify(
                publicKey: ByteArray,
                data: ByteArray,
                signature: ByteArray,
            ) = IdentitySignature.verify(publicKey, data, signature)
        }

        private fun ECPublicKey.uncompressedPoint(): ByteArray =
            byteArrayOf(0x04) + w.affineX.toByteArray().fitTo(32) + w.affineY.toByteArray()
                .fitTo(32)

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

    private companion object {
        val PROLOGUE = "hello-bytes".encodeToByteArray()
    }
}
