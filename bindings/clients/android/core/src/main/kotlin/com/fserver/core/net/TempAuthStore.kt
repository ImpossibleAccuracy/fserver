package com.fserver.core.net

import android.os.Build
import com.fserver.net.security.IdentityStore
import com.fserver.net.security.LocalIdentity
import java.util.UUID

class TempAuthStore : IdentityStore {
    override val local: LocalIdentity = LocalIdentity(
        deviceId = UUID.randomUUID().toString(),
        displayName = Build.MODEL,
        publicKey = "abcd".toByteArray(),
    )
}
