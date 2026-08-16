package com.fserver.core.net.adapter

import com.fserver.core.domain.model.connection.device.LocalDevice
import com.fserver.core.domain.store.LocalIdentityStore
import com.fserver.net.security.crypto.IdentitySignature
import com.fserver.net.security.identity.IdentityStore
import com.fserver.net.security.identity.LocalIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.Signature

/** Presents the host's [LocalIdentityStore] as the [IdentityStore] `:net` asks for. */
internal class IdentityStoreAdapter(
    private val localIdentityStore: LocalIdentityStore,
) : IdentityStore {
    private val keys by lazy { localIdentityStore.identityKeyPair() }
    private val publicKey by lazy { IdentitySignature.encodePublicKey(keys.public) }

    @Volatile
    private var cached: LocalIdentity? = null
    private val cacheLock = Mutex()

    override suspend fun local(): LocalIdentity = cached ?: cacheLock.withLock {
        cached ?: localIdentityStore.localDevice()
            .toIdentity(publicKey)
            .also { cached = it }
    }

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
