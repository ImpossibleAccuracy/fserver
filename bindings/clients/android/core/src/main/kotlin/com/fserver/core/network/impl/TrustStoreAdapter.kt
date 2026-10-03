package com.fserver.core.network.impl

import com.fserver.core.journal.JournalEvent
import com.fserver.core.journal.impl.JournalWriter
import com.fserver.core.network.auth.AuthMethod
import com.fserver.core.network.device.model.DeviceKind
import com.fserver.core.network.device.model.DeviceMetadata
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
    private val journal: JournalWriter,
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
        val paired = trustedDevicesStore.findByDeviceId(record.deviceId).isEmpty()
        val device = record.toDevice(lastSeen = timeProvider.now())
        trustedDevicesStore.upsert(device)

        // The first key of a device; another key of a known one is not a new pairing.
        if (paired) journal.record(JournalEvent.DevicePaired(device.deviceId, device.displayName, device.method))

        // Device-level, and written on its own so a connect that lands either side of the
        // handshake does not have to know the peer's claims to record the network.
        trustedDevicesStore.recordLastNetwork(
            deviceId = record.deviceId,
            networkId = networkInfoRepository.currentNetworkId(),
        )
    }
}

/** [lastSeen] is this pin's own moment; `lastNetworkId` is written separately - see [TrustStoreAdapter.pin]. */
private fun TrustRecord.toDevice(lastSeen: Instant) = TrustedDevice(
    deviceId = deviceId,
    displayName = displayName,
    publicKey = publicKey,
    method = AuthMethod.fromId(method) ?: error("Unknown auth method $method"),
    strength = strength.name,
    metadata = descriptor?.let {
        DeviceMetadata(
            kind = DeviceKind.fromSerialized(it.kind),
            dictionaryId = it.dictionary.id,
            dictionaryVersion = it.dictionary.version,
            lastSeen = lastSeen,
            lastNetworkId = null,
        )
    },
)

private fun TrustedDevice.toRecord() = TrustRecord(
    deviceId = deviceId,
    displayName = displayName,
    publicKey = publicKey,
    method = method.authMethodId,
    strength = AuthStrength.valueOf(strength),
    descriptor = null,
)
