package com.fserver.core.storage.internal

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Wraps data keys under a key the app's data does not hold. */
internal interface KeyWrapper {
    /** Names the wrapping key, stored next to what it wrapped. */
    val alias: String

    /** [aad] binds the wrapped key to its id, so two rows cannot swap keys. */
    fun wrap(plain: ByteArray, aad: ByteArray): Wrapped

    fun unwrap(wrapped: Wrapped, aad: ByteArray): ByteArray

    class Wrapped(val iv: ByteArray, val bytes: ByteArray)
}

/**
 * AES-256-GCM under an Android Keystore key. No user authentication or unlocked-device
 * requirement: sync runs in the background, behind the lock screen.
 */
internal class AndroidKeystoreWrapper(override val alias: String = DefaultAlias) : KeyWrapper {
    private val key: SecretKey by lazy { existing() ?: generate() }

    override fun wrap(plain: ByteArray, aad: ByteArray): KeyWrapper.Wrapped {
        // Keystore picks the IV itself: caller-provided ones are refused by default.
        val cipher = Cipher.getInstance(Transformation).apply {
            init(Cipher.ENCRYPT_MODE, key)
            updateAAD(aad)
        }
        return KeyWrapper.Wrapped(iv = cipher.iv, bytes = cipher.doFinal(plain))
    }

    override fun unwrap(wrapped: KeyWrapper.Wrapped, aad: ByteArray): ByteArray =
        Cipher.getInstance(Transformation).run {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, wrapped.iv))
            updateAAD(aad)
            doFinal(wrapped.bytes)
        }

    private fun existing(): SecretKey? {
        val keyStore = KeyStore.getInstance(Provider).apply { load(null) }
        return (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun generate(): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, Provider).run {
        init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        generateKey()
    }

    private companion object {
        const val DefaultAlias = "fserver.storage.wrap"
        const val Provider = "AndroidKeyStore"
        const val Transformation = "AES/GCM/NoPadding"
    }
}
