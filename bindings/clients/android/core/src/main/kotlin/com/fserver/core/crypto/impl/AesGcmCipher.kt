package com.fserver.core.crypto.impl

import com.fserver.core.crypto.spi.StorageCipher
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The built-in method. ChaCha20-Poly1305 would need API 28 or a third-party provider. */
internal object AesGcmCipher : StorageCipher {
    override val id = "fserver.aes256gcm-seg.v1"
    override val nonceSize = 12
    override val tagSize = 16

    override fun seal(
        key: SecretKey,
        nonce: ByteArray,
        aad: ByteArray,
        plain: ByteArray
    ): ByteArray =
        cipher(Cipher.ENCRYPT_MODE, key, nonce, aad).doFinal(plain)

    override fun open(
        key: SecretKey,
        nonce: ByteArray,
        aad: ByteArray,
        sealed: ByteArray
    ): ByteArray =
        cipher(Cipher.DECRYPT_MODE, key, nonce, aad).doFinal(sealed)

    private fun cipher(mode: Int, key: SecretKey, nonce: ByteArray, aad: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(tagSize * 8, nonce))
            updateAAD(aad)
        }
}
