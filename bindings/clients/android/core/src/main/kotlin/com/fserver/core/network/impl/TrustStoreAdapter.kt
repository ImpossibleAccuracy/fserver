package com.fserver.core.network.impl

import com.fserver.core.network.auth.AuthMethod
import com.fserver.core.network.device.model.TrustedDevice
import com.fserver.core.store.network.TrustedDevicesStore
import com.fserver.net.security.trust.AuthStrength
import com.fserver.net.security.trust.PeerTrustStore
import com.fserver.net.security.trust.TrustRecord
import kotlin.time.Clock
import kotlin.time.Instant

internal class TrustStoreAdapter(
    private val trustedDevicesStore: TrustedDevicesStore,
) : PeerTrustStore {
    override suspend fun find(publicKey: ByteArray): TrustRecord? =
        trustedDevicesStore.findByKey(publicKey)?.toRecord()

    override suspend fun findByDeviceId(deviceId: String): List<TrustRecord> =
        trustedDevicesStore.findByDeviceId(deviceId).map { it.toRecord() }

    override suspend fun pin(record: TrustRecord) {
        val now = Clock.System.now()
        trustedDevicesStore.upsert(
            record.toDevice(now)
        )
    }
}

private fun TrustRecord.toDevice(lastSeen: Instant) = TrustedDevice(
    deviceId = deviceId,
    displayName = displayName,
    publicKey = publicKey,
    method = AuthMethod.fromId(method) ?: error("Unknown auth method $method"),
    strength = strength.name,
    lastSeen = lastSeen,
)

private fun TrustedDevice.toRecord() = TrustRecord(
    deviceId = deviceId,
    displayName = displayName,
    publicKey = publicKey,
    method = method.authMethodId,
    strength = AuthStrength.valueOf(strength),
)
