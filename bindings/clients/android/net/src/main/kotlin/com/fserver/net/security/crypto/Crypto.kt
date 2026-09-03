package com.fserver.net.security.crypto

import java.security.SecureRandom

/**
 * Swappable crypto. Replacing this is the whole point: nothing above it knows how a frame is
 * protected, only that it is.
 */
interface CryptoProvider {
    val suite: Suite

    fun newKeyExchange(): KeyExchange

    fun aead(sharedSecret: ByteArray, role: Role): Aead

    /** Names the primitives a session ended up using; carried in [com.fserver.net.security.NegotiatedParameters]. */
    data class Suite(
        val name: String,
        /** False means frames go out as-is. Only acceptable while the transport itself is trusted. */
        val isEncrypting: Boolean,
    )

    /** One key agreement. A fresh instance per handshake - never reuse the ephemeral key. */
    interface KeyExchange {
        val publicKey: ByteArray
        fun sharedSecret(peerPublicKey: ByteArray): ByteArray
    }

    /** Frame-level seal/open. Failure to open is a protocol failure, not a dropped frame. */
    interface Aead {
        /**
         * Bytes [seal] adds to a frame. What fits in one frame is the transport's limit minus
         * this, so a suite that hides the figure would have callers guess at it.
         */
        val overhead: Int

        fun seal(plaintext: ByteArray): ByteArray
        fun open(ciphertext: ByteArray): ByteArray
    }

    /** Which side of the handshake this end is on - the two derive different directional keys. */
    enum class Role {
        Initiator,
        Responder;

        fun reverse(): Role = when (this) {
            Initiator -> Responder
            Responder -> Initiator
        }
    }
}

/**
 * Placeholder that moves frames unchanged.
 *
 * Everything around it - the key exchange fields in the handshake, the identity, the
 * fingerprint - is already wired, so swapping in X25519 + ChaCha20-Poly1305 later touches this
 * file only. Until then a session is authenticated as far as the peer chose to describe
 * itself, and not private: do not ship a client on this.
 */
object PassthroughCryptoProvider : CryptoProvider {
    override val suite: CryptoProvider.Suite =
        CryptoProvider.Suite(name = "passthrough", isEncrypting = false)

    override fun newKeyExchange(): CryptoProvider.KeyExchange = object :
        CryptoProvider.KeyExchange {
        override val publicKey: ByteArray = ByteArray(KEY_SIZE).also(SecureRandom()::nextBytes)

        override fun sharedSecret(peerPublicKey: ByteArray): ByteArray = ByteArray(KEY_SIZE)
    }

    override fun aead(sharedSecret: ByteArray, role: CryptoProvider.Role): CryptoProvider.Aead =
        object :
            CryptoProvider.Aead {
            override val overhead: Int = 0
            override fun seal(plaintext: ByteArray): ByteArray = plaintext
            override fun open(ciphertext: ByteArray): ByteArray = ciphertext
        }

    private const val KEY_SIZE = 32
}
