package com.fserver.core.store.network

import com.fserver.core.network.device.model.KnownRoute
import com.fserver.core.network.device.model.TrustedDevice
import com.fserver.core.store.FServerStorageApi
import kotlinx.coroutines.flow.Flow

/**
 * What a completed handshake leaves behind. Lookups and writes only - listing and forgetting are
 * UI actions and live on the repository.
 */
@SubclassOptInRequired(FServerStorageApi::class)
interface TrustedDevicesStore {
    val knownDeviceIds: Flow<Set<String>>

    suspend fun findByKey(publicKey: ByteArray): TrustedDevice?

    suspend fun findByDeviceId(deviceId: String): List<TrustedDevice>

    suspend fun upsert(record: TrustedDevice)

    /**
     * Remembers how [deviceId] was last reached over [KnownRoute.transport], replacing whatever
     * was known for that transport and leaving the other transports' routes alone.
     *
     * Called on every connection, probe, and accepted inbound session, so it must be cheap and
     * must tolerate being handed the same route again.
     */
    suspend fun recordKnownRoute(deviceId: String, route: KnownRoute)

    /** null when nothing dialable was ever recorded for [deviceId], or the device was forgotten. */
    suspend fun findKnownRoute(deviceId: String): KnownRoute?

    /** Remembers the network [deviceId] was last reached over, replacing whatever was recorded for it. */
    suspend fun recordLastNetwork(deviceId: String, networkId: String?)
}
