package com.fserver.net.transport.android.datasource.nearbyconnection

import android.content.Context
import com.fserver.net.security.LocalIdentity
import com.fserver.net.transport.android.spi.nearbyconnection.NearbyConnectionsSPI
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach

/**
 * The single seam onto Nearby Connections.
 */
internal class NearbyConnectionsRepository internal constructor(
    private val context: Context,
    private val config: NearbyConnectionsSPI.Config,
) {
    private val discoveryService = NearbyConnectionsDiscoveryService(context)
    private val advertisingService = NearbyConnectionsAdvertisingService(context)
    private val messenger = NearbyConnectionsMessenger(context)

    private val discoveryEvents = MutableSharedFlow<NCDiscoveryEvent>()
    private val advertisingEvents = MutableSharedFlow<NCAdvertiserEvent>()

    val events = merge(discoveryEvents, advertisingEvents)
        .filterIsInstance<NCDeviceEvent>()

    fun startDiscovery(identity: LocalIdentity) = discoveryService
        .start(identity, config)
        .onEach { discoveryEvents.emit(it) }

    fun startAdvertising(identity: LocalIdentity) = advertisingService
        .start(identity, config)
        .onEach { advertisingEvents.emit(it) }

    suspend fun send(endpointId: String, bytes: ByteArray): Result<Unit> =
        messenger.send(endpointId, bytes)

    fun accept(endpointId: String) = advertisingService.accept(endpointId)

    fun reject(endpointId: String) = advertisingService.reject(endpointId)

    fun disconnect(endpointId: String) = messenger.disconnect(endpointId)
}
