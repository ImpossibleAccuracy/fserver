package com.fserver.core.storage

import com.fserver.core.network.device.model.KnownRoute
import com.fserver.core.network.device.model.TrustedDevice
import kotlinx.coroutines.flow.Flow

/**
 * Trust records, as a screen needs them. Disjoint from `TrustedDevicesStore` on purpose: the
 * engine looks records up and writes them, the UI lists them and throws them away.
 */
interface TrustedDevicesRepository {
    /** Every key trusted on this device, most recently seen first. */
    val devices: Flow<List<TrustedDevice>>

    /** null when nothing dialable was ever recorded for [deviceId], or the device was forgotten. */
    fun observeKnownRoute(deviceId: String): Flow<KnownRoute?>

    /**
     * Drops every key recorded for [deviceId], and its known route with them. The next handshake
     * starts over from a code comparison, which is the only way to revoke trust.
     */
    suspend fun forget(deviceId: String)
}
