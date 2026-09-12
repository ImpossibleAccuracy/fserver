package com.fserver.net.security.identity

import com.fserver.common.exception.NetworkException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.UUID

/**
 * Identity that lives for one process. Enough to get the handshake running; a real host persists
 * its key pair instead, so a peer that trusted this device once still recognizes it.
 *
 * Signs with a real in-memory P-256 pair the same way a KeyStore-backed store would
 * (`SHA256withECDSA`, public key as an uncompressed SEC1 point).
 */
class EphemeralIdentityStore(
    val deviceId: String = UUID.randomUUID().toString(),
    val displayName: String = "unnamed device",
    val kind: String? = null,
) : IdentityStore {
    private val keys = KeyPairGenerator.getInstance("EC")
        .apply { initialize(ECGenParameterSpec("secp256r1")) }
        .generateKeyPair()

    override suspend fun local(): LocalIdentity = LocalIdentity(
        deviceId = deviceId,
        displayName = displayName,
        publicKey = (keys.public as ECPublicKey).uncompressedPoint(),
        kind = kind,
    )

    override suspend fun sign(data: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").run {
            initSign(keys.private)
            update(data)
            sign()
        }

    override suspend fun verify(publicKey: ByteArray, data: ByteArray, signature: ByteArray) {
        val valid = try {
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(publicKey.toPublicKey())
                update(data)
                verify(signature)
            }
        } catch (e: Exception) {
            throw NetworkException.AuthenticationRejected("identity proof failed", e)
        }

        if (!valid) throw NetworkException.AuthenticationRejected("identity proof failed")
    }

    /** Uncompressed SEC1 point back into a JCA key, using this store's own curve parameters. */
    private fun ByteArray.toPublicKey() = KeyFactory.getInstance("EC").generatePublic(
        ECPublicKeySpec(
            ECPoint(
                java.math.BigInteger(1, copyOfRange(1, 1 + COORDINATE_SIZE)),
                java.math.BigInteger(1, copyOfRange(1 + COORDINATE_SIZE, size)),
            ),
            (keys.public as ECPublicKey).params,
        )
    )

    private fun ECPublicKey.uncompressedPoint(): ByteArray =
        byteArrayOf(0x04) + w.affineX.toByteArray().fitTo(COORDINATE_SIZE) +
                w.affineY.toByteArray().fitTo(COORDINATE_SIZE)

    /** BigInteger.toByteArray pads with a sign byte or comes up short - pin to exactly [length]. */
    private fun ByteArray.fitTo(length: Int): ByteArray = when {
        size == length -> this
        size > length -> copyOfRange(size - length, size)
        else -> ByteArray(length - size) + this
    }

    private companion object {
        const val COORDINATE_SIZE = 32
    }
}
