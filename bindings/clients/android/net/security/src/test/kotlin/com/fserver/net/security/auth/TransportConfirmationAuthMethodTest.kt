package com.fserver.net.security.auth

import com.fserver.common.exception.NetworkException
import com.fserver.net.security.auth.transport.TransportConfirmationAuthMethod
import com.fserver.net.security.crypto.CryptoProvider
import com.fserver.net.security.crypto.X25519CryptoProvider
import com.fserver.net.security.crypto.IdentitySignature
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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

class TransportConfirmationAuthMethodTest {

    private val trustAll: TrustCheck = { _, _ -> }

    @Test
    fun `both sides derive the same session key and resolve each other's identity`() = runTest {
        val (aliceIo, bobIo) = pairedIo()
        val alice = TestPeer("alice")
        val bob = TestPeer("bob")

        val aliceOutcome = runSide(aliceIo, CryptoProvider.Role.Initiator, alice)
        val bobOutcome = runSide(bobIo, CryptoProvider.Role.Responder, bob)

        val aliceResult = aliceOutcome.await().getOrThrow()
        val bobResult = bobOutcome.await().getOrThrow()

        assertArrayEquals(aliceResult.sharedSecret, bobResult.sharedSecret)
        assertEquals(bob.identity.deviceId, aliceResult.peer.deviceId)
        assertEquals(alice.identity.deviceId, bobResult.peer.deviceId)
    }

    @Test
    fun `a peer claiming a key it cannot sign for is rejected`() = runTest {
        // What the transport secures is the link, so reaching the other end of one says nothing
        // about which device is there. A public key is not a secret: replaying a pinned peer's key
        // is the whole attack, and only the signature stops it.
        val (aliceIo, malloryIo) = pairedIo()
        val victim = TestPeer("bob")
        val mallory = TestPeer("mallory")

        val aliceOutcome = runSide(aliceIo, CryptoProvider.Role.Initiator, TestPeer("alice"))
        val malloryOutcome = runSide(
            io = malloryIo,
            role = CryptoProvider.Role.Responder,
            peer = mallory,
            claimed = victim.identity,
        )

        assertTrue(aliceOutcome.await().isFailure)

        // Mallory is left waiting on the code exchange Alice never reaches.
        malloryOutcome.cancel()
    }

    @Test
    fun `a transport that derived no code cannot run this method`() = runTest {
        val (aliceIo, _) = pairedIo()
        val alice = TestPeer("alice")

        val failure = runCatching {
            TransportConfirmationAuthMethod(X25519CryptoProvider).run(
                io = aliceIo,
                context = context(CryptoProvider.Role.Initiator, alice.identity, null),
            )
        }.exceptionOrNull()

        assertTrue(failure is NetworkException.Handshake)
    }

    /**
     * One side of a handshake: the method, then what the handshake itself does with what the
     * method reached - prove the identities, gate the peer, run the confirmation.
     */
    private fun CoroutineScope.runSide(
        io: HandshakeIo,
        role: CryptoProvider.Role,
        peer: TestPeer,
        claimed: LocalIdentity = peer.identity,
        trust: TrustCheck = trustAll,
    ): Deferred<Result<SideResult>> = async {
        runCatching {
            val context = context(role, claimed, CODE)
            val outcome = TransportConfirmationAuthMethod(X25519CryptoProvider).run(io, context)
            val proven = IdentityExchange.run(io, context, outcome, peer.store(claimed))

            trust(proven, outcome.confirmationCode)
            outcome.confirm()

            SideResult(sharedSecret = outcome.sharedSecret(), peer = proven)
        }
    }

    private class SideResult(val sharedSecret: ByteArray, val peer: PeerIdentity)

    private fun context(
        role: CryptoProvider.Role,
        claimed: LocalIdentity,
        code: String?,
    ) = AuthContext(
        role = role,
        request = null,
        prologue = PROLOGUE,
        confirmationCode = code,
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
        const val CODE = "482193"
    }
}
