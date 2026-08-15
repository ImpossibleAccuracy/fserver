package com.fserver.net.security.crypto

import com.fserver.net.NetworkException
import org.junit.Assert.fail
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

/**
 * Signatures here are produced through JCA (`SHA256withECDSA`), the exact path a KeyStore-backed
 * [com.fserver.net.security.identity.IdentityStore] takes - so these tests are the interop check
 * between platform signing and BC verification.
 */
class IdentitySignatureTest {

    private val keys = p256KeyPair()
    private val publicKey = (keys.public as ECPublicKey).uncompressedPoint()
    private val data = "signed-handshake-transcript".encodeToByteArray()

    @Test
    fun `accepts a JCA-produced signature`() {
        IdentitySignature.verify(publicKey, data, sign(keys, data))
    }

    @Test
    fun `rejects a signature over different data`() {
        assertRejected {
            IdentitySignature.verify(publicKey, "other-transcript".encodeToByteArray(), sign(keys, data))
        }
    }

    @Test
    fun `rejects a signature from a different key`() {
        assertRejected { IdentitySignature.verify(publicKey, data, sign(p256KeyPair(), data)) }
    }

    @Test
    fun `rejects garbage instead of passing or crashing`() {
        assertRejected { IdentitySignature.verify(publicKey, data, ByteArray(70)) }
        assertRejected { IdentitySignature.verify(publicKey, data, ByteArray(0)) }
        assertRejected { IdentitySignature.verify(ByteArray(65), data, sign(keys, data)) }
        assertRejected { IdentitySignature.verify(ByteArray(0), data, sign(keys, data)) }
    }

    @Test
    fun `rejects a compressed point encoding of a valid key`() {
        // One canonical encoding per key, or the same identity shows two fingerprints.
        val compressed = ByteArray(33).also {
            it[0] = if (publicKey.last().toInt() and 1 == 0) 0x02 else 0x03
            publicKey.copyInto(it, 1, 1, 33)
        }
        assertRejected { IdentitySignature.verify(compressed, data, sign(keys, data)) }
    }

    private fun assertRejected(block: () -> Unit) {
        try {
            block()
            fail("expected AuthenticationRejected")
        } catch (_: NetworkException.AuthenticationRejected) {
        }
    }

    private fun p256KeyPair(): KeyPair = KeyPairGenerator.getInstance("EC")
        .apply { initialize(ECGenParameterSpec("secp256r1")) }
        .generateKeyPair()

    private fun sign(keys: KeyPair, data: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").run {
            initSign(keys.private)
            update(data)
            sign()
        }

    private fun ECPublicKey.uncompressedPoint(): ByteArray =
        byteArrayOf(0x04) + w.affineX.toByteArray().fitTo(32) + w.affineY.toByteArray().fitTo(32)

    private fun ByteArray.fitTo(length: Int): ByteArray = when {
        size == length -> this
        size > length -> copyOfRange(size - length, size)
        else -> ByteArray(length - size) + this
    }
}
