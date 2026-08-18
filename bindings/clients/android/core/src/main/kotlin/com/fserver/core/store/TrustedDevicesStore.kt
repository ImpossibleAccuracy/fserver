package com.fserver.core.store

import com.fserver.core.network.device.model.KnownRoute
import com.fserver.core.network.device.model.TrustedDevice

/**
 * What a completed handshake leaves behind. Lookups and writes only - listing and forgetting are
 * UI actions and live on the repository.
 */
@SubclassOptInRequired(FServerStorageApi::class)
interface TrustedDevicesStore {
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
}
