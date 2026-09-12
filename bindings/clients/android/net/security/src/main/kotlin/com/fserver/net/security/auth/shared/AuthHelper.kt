package com.fserver.net.security.auth.shared

import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.SHA256

private const val DERIVED_KEY_SIZE = 32

/** The primitives the shipped methods share. Identity and trust are the handshake's, not theirs. */
object AuthHelper {
    val cryptographyProvider = CryptographyProvider.Default

    fun bind(
        secret: ByteArray,
        prologue: ByteArray,
        label: ByteArray
    ): ByteArray = cryptographyProvider.get(SHA256).hasher()
        .createHashFunction().use {
            it.update(label)
            it.update(secret)
            it.update(prologue)

            it.hashToByteArray()
        }

    suspend fun deriveKey(
        secret: ByteArray,
        info: ByteArray,
        keySize: Int = DERIVED_KEY_SIZE
    ): ByteArray = cryptographyProvider.get(HKDF)
        .secretDerivation(
            digest = SHA256,
            outputSize = keySize.bytes,
            salt = null,
            info = info,
        )
        .deriveSecretToByteArray(secret)
}
