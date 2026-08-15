package com.fserver.net.security.crypto

import com.fserver.net.NetworkException
import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.SecureRandom

/**
 * X25519 key agreement + ChaCha20-Poly1305 AEAD, replacing [PassthroughCryptoProvider].
 *
 * Built on Bouncy Castle's lightweight API (`org.bouncycastle.crypto.*`) rather than `javax.crypto`
 * on purpose: XDH and ChaCha20-Poly1305 are only backed by Conscrypt from API 31 and API 28
 * respectively, so a JCA `Cipher`/`KeyAgreement` call would pass on the host JVM used by `:net:test`
 * and throw on real devices between minSdk 24 and there. The lightweight API needs no JCA Provider
 * registration and behaves identically on both.
 */
object X25519CryptoProvider : CryptoProvider {
    override val suite: CryptoProvider.Suite =
        CryptoProvider.Suite(name = "x25519-chacha20poly1305", isEncrypting = true)

    override fun newKeyExchange(): CryptoProvider.KeyExchange =
        object : CryptoProvider.KeyExchange {
            private val private = X25519PrivateKeyParameters(SecureRandom())
            private val public = private.generatePublicKey()

            override val publicKey: ByteArray = public.encoded

            override fun sharedSecret(peerPublicKey: ByteArray): ByteArray {
                // BC silently zero-pads short keys and ignores extra bytes, so size is checked here.
                if (peerPublicKey.size != KEY_SIZE) {
                    throw NetworkException.Protocol("X25519 key is ${peerPublicKey.size} bytes, expected $KEY_SIZE")
                }
                val agreement = X25519Agreement().apply { init(private) }
                val secret = ByteArray(agreement.agreementSize)
                try {
                    agreement.calculateAgreement(X25519PublicKeyParameters(peerPublicKey, 0), secret, 0)
                } catch (e: IllegalStateException) {
                    // BC throws when the peer key is a low-order point and the secret would be all zeros.
                    throw NetworkException.Protocol("X25519 agreement failed", e)
                }
                return secret
            }
        }

    /**
     * One key in, two keys out - initiator-to-responder and responder-to-initiator traffic never
     * share a nonce space under the same key, even though a single [Aead] instance
     * both seals this side's frames and opens the peer's.
     */
    override fun aead(sharedSecret: ByteArray, role: CryptoProvider.Role): CryptoProvider.Aead =
        DirectionalAead(
            sendKey = directionalKey(sharedSecret, from = role),
            receiveKey = directionalKey(sharedSecret, from = role.other()),
        )

    private fun directionalKey(sharedSecret: ByteArray, from: CryptoProvider.Role): ByteArray {
        val out = ByteArray(KEY_SIZE)
        HKDFBytesGenerator(SHA256Digest()).apply {
            init(
                HKDFParameters(
                    sharedSecret,
                    null,
                    "fserver:${from.name.lowercase()}".encodeToByteArray()
                )
            )
        }.generateBytes(out, 0, out.size)
        return out
    }

    private fun CryptoProvider.Role.other(): CryptoProvider.Role = when (this) {
        CryptoProvider.Role.Initiator -> CryptoProvider.Role.Responder
        CryptoProvider.Role.Responder -> CryptoProvider.Role.Initiator
    }

    /** Nonce is a plain send/receive counter per direction - safe because each direction has its own key and frames are never reordered or dropped ahead of the seal (see [com.fserver.net.security.SecureChannel]). */
    private class DirectionalAead(
        private val sendKey: ByteArray,
        private val receiveKey: ByteArray,
    ) : CryptoProvider.Aead {
        private var sendCounter = 0L
        private var receiveCounter = 0L

        override fun seal(plaintext: ByteArray): ByteArray =
            run(sendKey, nonce(sendCounter++), forEncryption = true, plaintext)

        override fun open(ciphertext: ByteArray): ByteArray =
            run(receiveKey, nonce(receiveCounter++), forEncryption = false, ciphertext)

        private fun run(
            key: ByteArray,
            nonce: ByteArray,
            forEncryption: Boolean,
            input: ByteArray
        ): ByteArray {
            val cipher = ChaCha20Poly1305()
            cipher.init(forEncryption, AEADParameters(KeyParameter(key), TAG_BITS, nonce))
            val out = ByteArray(cipher.getOutputSize(input.size))
            try {
                val written = cipher.processBytes(input, 0, input.size, out, 0)
                cipher.doFinal(out, written)
            } catch (e: InvalidCipherTextException) {
                throw NetworkException.Protocol("frame failed to open", e)
            }
            return out
        }

        private fun nonce(counter: Long): ByteArray {
            val nonce = ByteArray(NONCE_SIZE)
            for (i in 0 until 8) {
                nonce[NONCE_SIZE - 1 - i] = (counter ushr (8 * i)).toByte()
            }
            return nonce
        }
    }

    private const val KEY_SIZE = 32
    private const val NONCE_SIZE = 12
    private const val TAG_BITS = 128
}