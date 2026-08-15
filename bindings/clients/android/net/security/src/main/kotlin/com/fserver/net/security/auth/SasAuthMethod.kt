package com.fserver.net.security.auth

import com.fserver.net.NetworkException
import com.fserver.net.security.PeerAuthenticator
import com.fserver.net.security.crypto.CryptoProvider
import com.fserver.net.security.identity.PeerIdentityCodec
import com.fserver.net.wire.ByteReader
import com.fserver.net.wire.ByteWriter
import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.SHA256
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Short Authentication String (SAS) authentication method.
 * Allows two peers to verify each other's identity by comparing a short code derived from their public keys.
 * This method is suitable for scenarios where users can manually compare codes, such as in a mobile app or web interface.
 */
class SasAuthMethod(
    private val crypto: CryptoProvider,
    private val authenticator: PeerAuthenticator?,
    private val confirmationCodeLength: Int = 8,
) : AuthMethod {
    private val cryptographyProvider = CryptographyProvider.Default
    override val id: AuthMethodId = ID

    override suspend fun run(io: HandshakeIo, context: AuthContext): AuthOutcome {
        val localKeyPair = crypto.newKeyExchange()
        val localNonce = ByteArray(32).also { SecureRandom().nextBytes(it) }

        // Compute hash of key, nonce and prologue to commit to our key and nonce before revealing them
        val commitment = commitmentHash(localKeyPair.publicKey, localNonce, context.prologue)
        val peerCommitment = io.exchange(commitment)

        // After commitment exchange, reveal our key and nonce to the peer
        io.send(SasMessage(localKeyPair.publicKey, localNonce).encode())
        val revealed = SasMessage.decode(io.receive())

        // Verify hash and actual keys match, otherwise the peer is cheating and we abort the handshake
        verifyCommitment(
            peerCommitment = peerCommitment,
            peerPublicKey = revealed.publicKey,
            peerNonce = revealed.nonce,
            prologue = context.prologue,
        )

        // Derive the shared secret using our private key and the peer's public key
        val sharedSecret = localKeyPair.sharedSecret(revealed.publicKey)

        // Bind prologue into shared secret to ensure both sides saw the same prologue
        val secretWithPrologue = bind(sharedSecret, context.prologue)
        val aead = crypto.aead(secretWithPrologue, context.role)
        // Send our identity and receive the peer's identity, using encrypted channel
        io.send(aead.seal(PeerIdentityCodec.encode(context.local)))
        val peer = PeerIdentityCodec.decode(aead.open(io.receive()))

        // Compute short code from secret and full
        val transcript = buildTranscript(
            prologue = context.prologue,
            role = context.role,
            localPublicKey = localKeyPair.publicKey,
            localNonce = localNonce,
            peerPublicKey = revealed.publicKey,
            peerNonce = revealed.nonce,
        )
        val sas = deriveSas(
            sharedSecret = sharedSecret,
            transcript = transcript,
        )

        val verdict = authenticator?.verify(peer, sas)
            ?: PeerAuthenticator.Decision.Trust
        if (verdict is PeerAuthenticator.Decision.Reject) {
            throw NetworkException.AuthenticationRejected(verdict.reason)
        }

        io.exchange(aead.seal(CONFIRMED))

        return AuthOutcome(
            sharedSecret = secretWithPrologue,
            peer = peer,
        )
    }

    private fun commitmentHash(
        publicKey: ByteArray,
        localNonce: ByteArray,
        prologue: ByteArray
    ): ByteArray = cryptographyProvider.get(SHA256).hasher()
        .createHashFunction().use {
            it.update(publicKey)
            it.update(localNonce)
            it.update(prologue)

            it.hashToByteArray()
        }

    private fun verifyCommitment(
        peerCommitment: ByteArray,
        peerPublicKey: ByteArray,
        peerNonce: ByteArray,
        prologue: ByteArray
    ) {
        val computedCommitment = commitmentHash(
            publicKey = peerPublicKey,
            localNonce = peerNonce,
            prologue = prologue
        )

        if (!MessageDigest.isEqual(peerCommitment, computedCommitment)) {
            throw NetworkException.AuthenticationRejected("commitment mismatch")
        }
    }

    private fun bind(secret: ByteArray, prologue: ByteArray): ByteArray =
        cryptographyProvider.get(SHA256).hasher()
            .createHashFunction().use {
                it.update(secret)
                it.update(prologue)

                it.hashToByteArray()
            }

    /**
     * Orders the transcript by role rather than "local"/"peer" - those flip depending on which
     * side is asking, so ordering by them would have each side hash the fields in a different
     * order and land on two different SAS codes even on an honest run.
     */
    private fun buildTranscript(
        prologue: ByteArray,
        role: CryptoProvider.Role,
        localPublicKey: ByteArray,
        localNonce: ByteArray,
        peerPublicKey: ByteArray,
        peerNonce: ByteArray,
    ): ByteArray {
        val (initiatorKey, initiatorNonce, responderKey, responderNonce) =
            if (role == CryptoProvider.Role.Initiator) {
                arrayOf(localPublicKey, localNonce, peerPublicKey, peerNonce)
            } else {
                arrayOf(peerPublicKey, peerNonce, localPublicKey, localNonce)
            }
        return prologue + initiatorKey + initiatorNonce + responderKey + responderNonce
    }

    private suspend fun deriveSas(
        sharedSecret: ByteArray,
        transcript: ByteArray,
    ): String {
        val hkdf = cryptographyProvider.get(HKDF)

        val derivation = hkdf.secretDerivation(
            digest = SHA256,
            outputSize = confirmationCodeLength.bytes,
            salt = null,
            info = transcript,
        )

        val codeBytes = derivation.deriveSecretToByteArray(sharedSecret)

        return buildString {
            // Convert bytes into numeric string
            for (b in codeBytes) {
                val number = b.toUByte().toInt() % 10

                append(number.toString())
            }
        }
    }

    companion object {
        val ID: AuthMethodId = AuthMethodId("sas-1")
        private val CONFIRMED = ByteArray(0)
        //private val HKDF_SALT = "sas-v1".encodeToByteArray()
    }
}

private class SasMessage(
    val publicKey: ByteArray,
    val nonce: ByteArray
) {
    fun encode(): ByteArray =
        ByteWriter(4 + publicKey.size + 4 + nonce.size)
            .bytes(publicKey)
            .bytes(nonce)
            .toByteArray()

    companion object Codec {
        fun decode(bytes: ByteArray): SasMessage {
            val reader = ByteReader(bytes)
            val publicKey = reader.bytes()
            val nonce = reader.bytes()
            return SasMessage(publicKey, nonce)
        }
    }
}
