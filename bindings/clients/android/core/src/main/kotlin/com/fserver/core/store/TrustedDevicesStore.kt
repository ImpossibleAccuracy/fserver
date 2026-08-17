package com.fserver.core.store

import com.fserver.core.network.device.model.TrustedDevice
import kotlinx.coroutines.flow.Flow

interface TrustedDevicesStore {
    /** Every key trusted on this device, most recently seen first. Live. */
    val devices: Flow<List<TrustedDevice>>

    suspend fun findByKey(publicKey: ByteArray): TrustedDevice?

    suspend fun findByDeviceId(deviceId: String): List<TrustedDevice>

    suspend fun upsert(record: TrustedDevice)

    /**
     * Drops the trust granted to [publicKey]. The next handshake with that key starts over from a
     * code comparison, which is the only way to revoke trust.
     */
    suspend fun delete(publicKey: ByteArray)
}
