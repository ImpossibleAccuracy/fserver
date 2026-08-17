package com.fserver.app.domain

import com.fserver.core.network.device.model.KnownRoute
import com.fserver.core.store.TrustedDevicesStore
import kotlinx.coroutines.flow.Flow

interface SavedDevicesRepository : TrustedDevicesStore {
    /** null when nothing dialable was ever recorded for [deviceId], or the device was forgotten. */
    fun findKnownRoute(deviceId: String): Flow<KnownRoute?>

    /**
     * Drops the trust granted to [publicKey]. The next handshake with that key starts over from a
     * code comparison, which is the only way to revoke trust.
     *
     * Dropping the device's last key also drops its known route.
     */
    suspend fun delete(publicKey: ByteArray)
}
