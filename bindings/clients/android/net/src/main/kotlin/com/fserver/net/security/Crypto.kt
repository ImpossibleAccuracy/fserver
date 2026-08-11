package com.fserver.net.security

import java.security.SecureRandom

/** Names the primitives a session ended up using; carried in [NegotiatedParameters]. */
data class CipherSuite(
    val name: String,
    /** False means the frames go out as-is. Only acceptable while the transport itself is trusted. */
    val isEncrypting: Boolean,
)

/** One key agreement. A fresh instance per handshake - never reuse the ephemeral key. */
interface KeyExchange {
    val publicKey: ByteArray
    fun sharedSecret(peerPublicKey: ByteArray): ByteArray
}

/** Frame-level seal/open. Failure to open is a protocol failure, not a dropped frame. */
interface Aead {
    fun seal(plaintext: ByteArray): ByteArray
    fun open(ciphertext: ByteArray): ByteArray
}

/** Which side of the handshake this end is on - the two derive different directional keys. */
enum class HandshakeRole { Initiator, Responder }

/**
 * Swappable crypto. Replacing this is the whole point: nothing above it knows how a frame is
 * protected, only that it is.
 */
interface CryptoProvider {
    val suite: CipherSuite
    fun newKeyExchange(): KeyExchange
    fun aead(sharedSecret: ByteArray, role: HandshakeRole): Aead
}

/**
 * Placeholder that moves frames unchanged.
 *
 * Everything around it - the key exchange fields in the handshake, the identity, the fingerprint -
 * is already wired, so swapping in X25519 + ChaCha20-Poly1305 later touches this file only. Until
 * then a session is authenticated as far as the peer chose to describe itself, and not private:
 * do not ship a client on this.
 */
object PassthroughCryptoProvider : CryptoProvider {
    override val suite: CipherSuite = CipherSuite(name = "passthrough", isEncrypting = false)

    override fun newKeyExchange(): KeyExchange = object : KeyExchange {
        override val publicKey: ByteArray = ByteArray(KEY_SIZE).also(SecureRandom()::nextBytes)

        override fun sharedSecret(peerPublicKey: ByteArray): ByteArray = ByteArray(KEY_SIZE)
    }

    override fun aead(sharedSecret: ByteArray, role: HandshakeRole): Aead = object : Aead {
        override fun seal(plaintext: ByteArray): ByteArray = plaintext
        override fun open(ciphertext: ByteArray): ByteArray = ciphertext
    }

    private const val KEY_SIZE = 32
}
