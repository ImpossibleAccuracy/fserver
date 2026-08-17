package com.fserver.core.store

import com.fserver.core.network.device.model.TrustedDevice

interface TrustedDevicesStore {
    suspend fun findByKey(publicKey: ByteArray): TrustedDevice?

    suspend fun findByDeviceId(deviceId: String): List<TrustedDevice>

    suspend fun upsert(record: TrustedDevice)
}
