package com.fserver.core.network.impl

import com.fserver.core.network.device.model.LocalDevice
import com.fserver.core.store.DeviceIdentityStore
import com.fserver.net.security.crypto.IdentitySignature
import com.fserver.net.security.identity.IdentityStore
import com.fserver.net.security.identity.LocalIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.Signature

/** Presents the host's [DeviceIdentityStore] as the [IdentityStore] `:net` asks for. */
internal class IdentityStoreAdapter(
    private val deviceIdentityStore: DeviceIdentityStore,
) : IdentityStore {
    private val keys by lazy { deviceIdentityStore.identityKeyPair }
    private val publicKey by lazy { IdentitySignature.encodePublicKey(keys.public) }

    override suspend fun local(): LocalIdentity =
        deviceIdentityStore.localDevice.load().toIdentity(publicKey)

    override suspend fun sign(data: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        Signature.getInstance("SHA256withECDSA").run {
            initSign(keys.private)
            update(data)
            sign()
        }
    }
}

private fun LocalDevice.toIdentity(
    publicKey: ByteArray,
) = LocalIdentity(
    deviceId = deviceId,
    displayName = displayName,
    publicKey = publicKey,
    kind = kind?.serialized,
)
