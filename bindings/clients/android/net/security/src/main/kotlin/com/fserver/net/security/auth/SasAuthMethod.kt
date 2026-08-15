package com.fserver.net.security.auth

import com.fserver.net.NetworkException
import com.fserver.net.security.PeerAuthenticator
import com.fserver.net.security.crypto.CryptoProvider
import com.fserver.net.security.crypto.IdentitySignature
import com.fserver.net.security.identity.PeerIdentity
import com.fserver.net.security.identity.PeerIdentityCodec
import com.fserver.net.wire.ByteReader
import com.fserver.net.wire.ByteWriter
import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.SHA256
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Short Authentication String (SAS) authentication method.
 * Allows two peers to verify each other's identity by comparing a short code derived from their public keys.
 * This method is suitable for scenarios where users can manually compare codes, such as in a mobile app or web interface.
 *
 * The peer identity travels sealed and is proven: each side signs the transcript with its
 * identity key, so a completed handshake vouches for the claimed key, not just the channel.
 */
class SasAuthMethod(
    private val crypto: CryptoProvider,
    private val authenticator: PeerAuthenticator?,
    private val confirmationCodeLength: Int = 8,
) : AuthMethod {
    private val cryptographyProvider = CryptographyProvider.Default
    override val id: AuthMethodId = ID

    init {
        requireNotNull(authenticator) {
            "SAS authentication requires a PeerAuthenticator to verify the short code. Disable SAS or provide a PeerAuthenticator to use this method."
        }

        require(confirmationCodeLength in 4..16) {
            "confirmationCodeLength must be between 4 and 16, inclusive"
        }
    }

    override suspend fun run(io: HandshakeIo, context: AuthContext): AuthOutcome {
        val localKeyPair = crypto.newKeyExchange()
        val localNonce = ByteArray(NONCE_SIZE).also { SecureRandom().nextBytes(it) }

        // Commit to the exact reveal bytes before revealing them. The length-prefixed encoding
        // plus the strict decode below leave the peer exactly one valid (key, nonce) parse, so a
        // commitment cannot be re-split into another pair after our reveal to grind the SAS.
        val localReveal = SasMessage(localKeyPair.publicKey, localNonce).encode()
        val commitment = commitmentHash(localReveal, context.prologue)
        val peerCommitment = io.exchange(commitment)

        // After commitment exchange, reveal our key and nonce to the peer
        io.send(localReveal)
        val peerReveal = io.receive()

        // Verify hash and actual keys match, otherwise the peer is cheating and we abort the handshake
        verifyCommitment(peerCommitment, peerReveal, context.prologue)
        val revealed = SasMessage.decode(peerReveal, expectedKeySize = localKeyPair.publicKey.size)

        // Derive the shared secret using our private key and the peer's public key
        val sharedSecret = localKeyPair.sharedSecret(revealed.publicKey)

        // Bind prologue into shared secret to ensure both sides saw the same prologue
        val secretWithPrologue = bind(sharedSecret, context.prologue)

        // Handshake and session must never share AEAD keys: the session channel restarts nonce
        // counters at zero, so keying both from the same secret would reuse (key, nonce) pairs.
        val aead = crypto.aead(deriveKey(secretWithPrologue, HANDSHAKE_KEY_INFO), context.role)

        // Create full transcript of the handshake
        val transcript = buildTranscript(
            prologue = context.prologue,
            role = context.role,
            localPublicKey = localKeyPair.publicKey,
            localNonce = localNonce,
            peerPublicKey = revealed.publicKey,
            peerNonce = revealed.nonce,
        )

        val peer = receivePeerIdentity(context, transcript, io, aead)

        // Compute short code
        val sas = deriveSas(
            sharedSecret = sharedSecret,
            transcript = transcript,
        )

        val verdict = authenticator!!.verify(peer, sas)
        if (verdict is PeerAuthenticator.Decision.Reject) {
            throw NetworkException.AuthenticationRejected(verdict.reason)
        }

        if (!aead.open(io.exchange(aead.seal(CONFIRMED))).contentEquals(CONFIRMED)) {
            throw NetworkException.AuthenticationRejected("peer did not confirm SAS")
        }

        return AuthOutcome(
            sharedSecret = deriveKey(secretWithPrologue, SESSION_KEY_INFO),
            peer = peer,
        )
    }

    /**
     * Send our identity using private key to sign the transcript and prove we are the owner of the identity.
     * The peer will verify the signature using the public key in the identity.
     */
    private suspend fun receivePeerIdentity(
        context: AuthContext,
        transcript: ByteArray,
        io: HandshakeIo,
        aead: CryptoProvider.Aead,
    ): PeerIdentity {
        val sendIdentity = suspend {
            val identityBytes = PeerIdentityCodec.encode(context.local)
            val signature = context.sign(popData(context.role, transcript, identityBytes))
            io.send(
                aead.seal(
                    EncryptedIdentity(identityBytes, signature).encode()
                )
            )
        }

        val receiveIdentity = suspend {
            val encryptedPeerIdentity = EncryptedIdentity.decode(aead.open(io.receive()))
            PeerIdentityCodec.decode(encryptedPeerIdentity.identity)
                .also { peer ->
                    val peerRole = if (context.role == CryptoProvider.Role.Initiator)
                        CryptoProvider.Role.Responder else CryptoProvider.Role.Initiator

                    // Verify before returning
                    IdentitySignature.verify(
                        publicKey = peer.publicKey,
                        data = popData(peerRole, transcript, encryptedPeerIdentity.identity),
                        signature = encryptedPeerIdentity.signature
                    )
                }
        }

        return when (context.role) {
            CryptoProvider.Role.Initiator -> {
                // If we are initiator, send our identity first.
                sendIdentity()
                receiveIdentity()
            }

            CryptoProvider.Role.Responder -> {
                // If we are responder, verify peer's identity first.
                // It secures from active device enumeration on LAN
                receiveIdentity().also {
                    sendIdentity()
                }
            }
        }
    }

    private fun commitmentHash(
        reveal: ByteArray,
        prologue: ByteArray
    ): ByteArray = cryptographyProvider.get(SHA256).hasher()
        .createHashFunction().use {
            it.update(COMMITMENT_LABEL)
            it.update(reveal)
            it.update(prologue)

            it.hashToByteArray()
        }

    private fun verifyCommitment(
        peerCommitment: ByteArray,
        peerReveal: ByteArray,
        prologue: ByteArray
    ) {
        val computedCommitment = commitmentHash(peerReveal, prologue)

        if (!MessageDigest.isEqual(peerCommitment, computedCommitment)) {
            throw NetworkException.AuthenticationRejected("commitment mismatch")
        }
    }

    private fun bind(secret: ByteArray, prologue: ByteArray): ByteArray =
        cryptographyProvider.get(SHA256).hasher()
            .createHashFunction().use {
                it.update(BIND_LABEL)
                it.update(secret)
                it.update(prologue)

                it.hashToByteArray()
            }

    private suspend fun deriveKey(secret: ByteArray, info: ByteArray): ByteArray =
        cryptographyProvider.get(HKDF)
            .secretDerivation(
                digest = SHA256,
                outputSize = DERIVED_KEY_SIZE.bytes,
                salt = null,
                info = info,
            )
            .deriveSecretToByteArray(secret)

    /**
     * Entire handshake transcript, including prologue, public keys, and nonce.
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
        return ByteWriter(prologue.size + initiatorKey.size + responderKey.size + 2 * NONCE_SIZE + 20)
            .bytes(prologue)
            .bytes(initiatorKey)
            .bytes(initiatorNonce)
            .bytes(responderKey)
            .bytes(responderNonce)
            .toByteArray()
    }

    /** Proof-of-possession data to sign, including the transcript and identity. */
    private fun popData(
        signer: CryptoProvider.Role,
        transcript: ByteArray,
        identityBytes: ByteArray,
    ): ByteArray = ByteWriter(POP_LABEL.size + transcript.size + identityBytes.size + 16)
        .raw(POP_LABEL)
        .u8(if (signer == CryptoProvider.Role.Initiator) 0 else 1)
        .bytes(transcript)
        .bytes(identityBytes)
        .toByteArray()

    /** Compute short authentication string from shared secret and handshake transcript. */
    private suspend fun deriveSas(
        sharedSecret: ByteArray,
        transcript: ByteArray,
    ): String {
        val hkdf = cryptographyProvider.get(HKDF)

        val derivation = hkdf.secretDerivation(
            digest = SHA256,
            // Extra bytes make the modulo reduction bias negligible.
            outputSize = (confirmationCodeLength + MODULO_BIAS_MARGIN).bytes,
            salt = null,
            info = transcript,
        )

        val codeBytes = derivation.deriveSecretToByteArray(sharedSecret)

        return BigInteger(1, codeBytes)
            .mod(BigInteger.TEN.pow(confirmationCodeLength))
            .toString()
            .padStart(confirmationCodeLength, '0')
    }

    companion object {
        val ID: AuthMethodId = AuthMethodId("sas-1")

        /** Control bytes to confirm both sides saw the same SAS code. */
        private val CONFIRMED = "CFD".encodeToByteArray()

        private val HANDSHAKE_KEY_INFO = "sas-1:handshake".encodeToByteArray()
        private val SESSION_KEY_INFO = "sas-1:session".encodeToByteArray()

        private val COMMITMENT_LABEL = "sas-1:commitment".encodeToByteArray()
        private val BIND_LABEL = "sas-1:bind".encodeToByteArray()
        private val POP_LABEL = "sas-1:pop".encodeToByteArray()

        internal const val NONCE_SIZE = 32
        private const val DERIVED_KEY_SIZE = 32
        private const val MODULO_BIAS_MARGIN = 8
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
        /** Strict: sizes pinned and no trailing bytes, or a commitment would bind more than one parse. */
        fun decode(bytes: ByteArray, expectedKeySize: Int): SasMessage {
            val reader = ByteReader(bytes)
            val publicKey = reader.bytes()
            val nonce = reader.bytes()
            if (publicKey.size != expectedKeySize) {
                throw NetworkException.Protocol("SAS reveal key is ${publicKey.size} bytes, expected $expectedKeySize")
            }
            if (nonce.size != SasAuthMethod.NONCE_SIZE) {
                throw NetworkException.Protocol("SAS reveal nonce is ${nonce.size} bytes, expected ${SasAuthMethod.NONCE_SIZE}")
            }
            if (reader.remaining != 0) {
                throw NetworkException.Protocol("SAS reveal has ${reader.remaining} trailing bytes")
            }
            return SasMessage(publicKey, nonce)
        }
    }
}

private class EncryptedIdentity(
    val identity: ByteArray,
    val signature: ByteArray
) {
    fun encode(): ByteArray =
        ByteWriter(4 + identity.size + 4 + signature.size)
            .bytes(identity)
            .bytes(signature)
            .toByteArray()

    companion object Codec {
        fun decode(bytes: ByteArray): EncryptedIdentity {
            val reader = ByteReader(bytes)
            val identity = reader.bytes()
            val signature = reader.bytes()
            if (reader.remaining != 0) {
                throw NetworkException.Protocol("Encrypted identity has ${reader.remaining} trailing bytes")
            }
            return EncryptedIdentity(identity, signature)
        }
    }
}
