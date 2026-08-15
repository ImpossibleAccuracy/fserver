package com.fserver.net.security.crypto

import com.fserver.net.NetworkException
import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.crypto.signers.DSADigestSigner
import org.bouncycastle.crypto.signers.ECDSASigner

/**
 * Verifies identity signatures: ECDSA P-256 + SHA-256, the strongest primitive Android KeyStore
 * backs at minSdk 24. Deliberately not part of [CryptoProvider] - the ephemeral suite is
 * negotiated per session, but an identity must verify identically under every suite.
 *
 * The public key is an uncompressed SEC1 point (65 bytes, `04 || X || Y`) - one canonical
 * encoding, so one key cannot present two fingerprints. The signature is DER, as JCA's
 * `SHA256withECDSA` emits. Any failure - malformed input included - throws
 * [NetworkException.AuthenticationRejected].
 */
object IdentitySignature {
    const val PUBLIC_KEY_SIZE = 65

    private val curve = SECNamedCurves.getByName("secp256r1")
    private val domain = ECDomainParameters(curve.curve, curve.g, curve.n, curve.h)

    fun verify(publicKey: ByteArray, data: ByteArray, signature: ByteArray) {
        if (publicKey.size != PUBLIC_KEY_SIZE || publicKey[0].toInt() != 0x04) {
            throw NetworkException.AuthenticationRejected("identity proof failed")
        }

        // DSADigestSigner returns false for a wrong or malformed signature (it swallows DER
        // errors internally) and throws only for a bad key - both must end in the same rejection.
        val valid = try {
            val signer = DSADigestSigner(ECDSASigner(), SHA256Digest())
            signer.init(false, ECPublicKeyParameters(domain.curve.decodePoint(publicKey), domain))
            signer.update(data, 0, data.size)
            signer.verifySignature(signature)
        } catch (e: Exception) {
            throw NetworkException.AuthenticationRejected("identity proof failed", e)
        }
        if (!valid) {
            throw NetworkException.AuthenticationRejected("identity proof failed")
        }
    }
}
