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
}
