package com.fserver.net.support

import com.fserver.net.security.crypto.CryptoProvider
import java.security.SecureRandom

/**
 * Toy AEAD: XOR under a shared mask, with a different mask per direction. Not secure - it exists
 * so a test can tell whether a frame actually went through the provider, and whether the two ends
 * derived the same secret and used opposite roles.
 */
class XorCryptoProvider : CryptoProvider {
    override val suite = CryptoProvider.Suite(name = "xor-test", isEncrypting = true)

    override fun newKeyExchange(): CryptoProvider.KeyExchange = object : CryptoProvider.KeyExchange {
        override val publicKey: ByteArray = ByteArray(KEY_SIZE).also(SecureRandom()::nextBytes)

        override fun sharedSecret(peerPublicKey: ByteArray): ByteArray =
            ByteArray(KEY_SIZE) { i -> (publicKey[i].toInt() xor peerPublicKey[i].toInt()).toByte() }
    }

    override fun aead(sharedSecret: ByteArray, role: CryptoProvider.Role): CryptoProvider.Aead {
        val sealMask = (sharedSecret[0].toInt() xor tweak(role)).toByte()
        val openMask = (sharedSecret[0].toInt() xor tweak(other(role))).toByte()

        return object : CryptoProvider.Aead {
            override val overhead: Int = 0
            override fun seal(plaintext: ByteArray) = mask(plaintext, sealMask)
            override fun open(ciphertext: ByteArray) = mask(ciphertext, openMask)
        }
    }

    private fun mask(bytes: ByteArray, mask: Byte) =
        ByteArray(bytes.size) { i -> (bytes[i].toInt() xor mask.toInt()).toByte() }

    private fun tweak(role: CryptoProvider.Role) =
        if (role == CryptoProvider.Role.Initiator) 0x01 else 0x02

    private fun other(role: CryptoProvider.Role) =
        if (role == CryptoProvider.Role.Initiator) CryptoProvider.Role.Responder
        else CryptoProvider.Role.Initiator

    private companion object {
        const val KEY_SIZE = 32
    }
}
