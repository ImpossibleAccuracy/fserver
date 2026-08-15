package com.fserver.core.net

import android.os.Build
import com.fserver.net.security.identity.IdentityStore
import com.fserver.net.security.identity.LocalIdentity
import java.util.UUID

class TempAuthStore : IdentityStore {
    override val local: LocalIdentity = LocalIdentity(
        deviceId = UUID.randomUUID().toString(),
        displayName = Build.MODEL,
        publicKey = Build.MODEL.toByteArray(),
    )

    // TODO: back with a KeyStore-held P-256 pair; publicKey above is not a real key either.
    override suspend fun sign(data: ByteArray): ByteArray =
        throw NotImplementedError("TempAuthStore has no signing key")
}
