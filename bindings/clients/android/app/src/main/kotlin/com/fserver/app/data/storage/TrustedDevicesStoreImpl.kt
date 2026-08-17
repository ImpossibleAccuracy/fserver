package com.fserver.app.data.storage

import com.fserver.app.database.FServerDatabase
import com.fserver.core.network.auth.AuthMethod
import com.fserver.core.network.device.model.TrustedDevice
import com.fserver.core.store.TrustedDevicesStore
import kotlin.time.Instant
import com.fserver.app.database.TrustedDevice as DBTrustedDevice

class TrustedDevicesStoreImpl(
    database: FServerDatabase,
) : TrustedDevicesStore {
    private val dao = database.trustedDeviceQueries

    override suspend fun findByKey(publicKey: ByteArray): TrustedDevice? =
        dao.findByKey(publicKey).executeAsOneOrNull()?.toDomainModel()

    override suspend fun findByDeviceId(deviceId: String): List<TrustedDevice> =
        dao.findByDeviceId(deviceId).executeAsList().map { it.toDomainModel() }

    override suspend fun upsert(record: TrustedDevice) {
        dao.upsert(
            publicKey = record.publicKey,
            deviceId = record.deviceId,
            displayName = record.displayName,
            method = record.method.name,
            strength = record.strength,
            lastSeenEpochMs = record.lastSeen.toEpochMilliseconds(),
        )
    }
}

private fun DBTrustedDevice.toDomainModel() = TrustedDevice(
    publicKey = publicKey,
    deviceId = deviceId,
    displayName = displayName,
    method = AuthMethod.valueOf(method),
    strength = strength,
    lastSeen = Instant.fromEpochMilliseconds(lastSeenEpochMs),
)
