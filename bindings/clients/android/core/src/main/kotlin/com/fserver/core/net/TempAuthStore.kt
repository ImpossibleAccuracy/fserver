package com.fserver.core.net

import android.content.Context
import android.os.Build
import com.fserver.net.security.crypto.IdentitySignature
import com.fserver.net.security.identity.IdentityStore
import com.fserver.net.security.identity.LocalIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyPair
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.util.UUID

internal class TempAuthStore(context: Context) : IdentityStore {
    private val keyPair: KeyPair = KeyPairStore(context).getOrCreateAndroidKeyPair(KEY_ALIAS)

    override val local: LocalIdentity = LocalIdentity(
        deviceId = UUID.randomUUID().toString(),
        displayName = Build.MODEL,
        publicKey = (keyPair.public as ECPublicKey).uncompressedPoint(),
    )

    override suspend fun sign(data: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(data)
            sign()
        }
    }

    /** Raw SEC1 point, not `keyPair.public.encoded` (X.509 DER) - see [IdentitySignature]. */
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
        const val KEY_ALIAS = "fserver.identity"
        const val COORDINATE_SIZE = 32
    }
}
