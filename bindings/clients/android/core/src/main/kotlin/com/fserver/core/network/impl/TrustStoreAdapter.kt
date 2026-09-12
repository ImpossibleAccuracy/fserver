package com.fserver.core.network.impl

import com.fserver.core.network.auth.AuthMethod
import com.fserver.core.network.device.model.TrustedDevice
import com.fserver.core.network.info.NetworkInfoRepository
import com.fserver.core.network.info.currentNetworkId
import com.fserver.core.store.network.TrustedDevicesStore
import com.fserver.core.util.TimeProvider
import com.fserver.net.security.trust.AuthStrength
import com.fserver.net.security.trust.PeerTrustStore
import com.fserver.net.security.trust.TrustRecord
import kotlin.time.Instant

internal class TrustStoreAdapter(
    private val trustedDevicesStore: TrustedDevicesStore,
    private val networkInfoRepository: NetworkInfoRepository,
    private val timeProvider: TimeProvider,
) : PeerTrustStore {
    override suspend fun find(publicKey: ByteArray): TrustRecord? =
        trustedDevicesStore.findByKey(publicKey)?.toRecord()

    override suspend fun findByDeviceId(deviceId: String): List<TrustRecord> =
        trustedDevicesStore.findByDeviceId(deviceId).map { it.toRecord() }

    /**
     * Every handshake commits a pin, incoming or outgoing, which is what keeps the network on the
     * record current: a reconnect from a different Wi-Fi overwrites the one before it.
     */
    override suspend fun pin(record: TrustRecord) {
        trustedDevicesStore.upsert(
            record.toDevice(
                lastSeen = timeProvider.now(),
                lastNetworkId = networkInfoRepository.currentNetworkId(),
            )
        )
    }
}

private fun TrustRecord.toDevice(lastSeen: Instant, lastNetworkId: String?) = TrustedDevice(
    deviceId = deviceId,
    displayName = displayName,
    publicKey = publicKey,
    method = AuthMethod.fromId(method) ?: error("Unknown auth method $method"),
    strength = strength.name,
    lastSeen = lastSeen,
    lastNetworkId = lastNetworkId,
)

private fun TrustedDevice.toRecord() = TrustRecord(
    deviceId = deviceId,
    displayName = displayName,
    publicKey = publicKey,
    method = method.authMethodId,
    strength = AuthStrength.valueOf(strength),
)
