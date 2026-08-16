package com.fserver.core.net

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec

/**
 * Android KeyStore-backed identity key. P-256 (`secp256r1`) + SHA-256, matching
 * [com.fserver.net.security.crypto.IdentitySignature] - the private key never leaves hardware.
 */
internal class KeyPairStore(val context: Context) {

    fun generateAndroidKeyPair(alias: String): KeyPair {
        val kpg = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            "AndroidKeyStore"
        )

        val parameterSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        ).run {
            setDigests(KeyProperties.DIGEST_SHA256)
            setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            build()
        }

        kpg.initialize(parameterSpec)
        return kpg.generateKeyPair()
    }

    fun getAndroidKeyPair(alias: String): KeyPair? {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        if (!keyStore.containsAlias(alias)) return null

        val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry ?: return null

        // Note: the private key bytes stay inside hardware; this object is a handle only.
        return KeyPair(entry.certificate.publicKey, entry.privateKey)
    }

    fun getOrCreateAndroidKeyPair(alias: String): KeyPair =
        getAndroidKeyPair(alias) ?: generateAndroidKeyPair(alias)
}
