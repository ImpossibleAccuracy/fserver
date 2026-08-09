package com.fserver.app.data.repository

import com.fserver.app.data.datasource.nearbyconnection.NearbyConnectionsAdvertisingService
import com.fserver.app.data.datasource.nearbyconnection.NearbyConnectionsDiscoveryService
import com.fserver.app.data.datasource.nearbyconnection.NearbyConnectionsEvent
import com.fserver.app.data.datasource.nearbyconnection.NearbyConnectionsMessage
import com.fserver.app.data.datasource.nearbyconnection.NearbyConnectionsMessenger
import com.fserver.app.data.datasource.nearbyconnection.NearbyConnectionsPeer
import com.fserver.app.data.detection.scan.NearbyConnectionsScanner
import com.fserver.app.data.utils.InternalConnectionApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The single seam onto Nearby Connections.
 */
internal class NearbyConnectionsRepository internal constructor(
    private val discoveryService: NearbyConnectionsDiscoveryService,
    private val advertisingService: NearbyConnectionsAdvertisingService,
    private val messenger: NearbyConnectionsMessenger,
    private val scope: CoroutineScope,
) {
    private var discoveryJob: Job? = null
    private var advertisingJob: Job? = null

    private val peers = MutableStateFlow<Map<String, PeerState>>(emptyMap())

    /**
     * Peers with a live connection, from either direction.
     * A peer appears here only after Nearby reports the connection established,
     * not when it is first seen.
     */
    val connectedDevices: Flow<List<NearbyConnectionsPeer>> = peers
        .map { states ->
            states.values
                .filter { it.isConnected }
                .map { it.peer }
        }
        .distinctUntilChanged()

    private val _incomingConnections = MutableSharedFlow<NearbyConnectionsPeer>(
        replay = EVENT_BUFFER,
        extraBufferCapacity = EVENT_BUFFER,
    )

    /**
     * Peers that asked *us* to connect, with their
     * [NearbyConnectionsPeer.authenticationDigits] still unconfirmed.
     * Answer each one with [accept] or [reject];
     * nothing here is connected until then.
     */
    val incomingConnections: Flow<NearbyConnectionsPeer> = _incomingConnections.asSharedFlow()

    /**
     * Payloads received over any live connection, whichever side opened it.
     */
    private val messages = MutableSharedFlow<NearbyConnectionsMessage>(
        extraBufferCapacity = EVENT_BUFFER,
    )

    private val _discoveryEvents = MutableSharedFlow<NearbyConnectionsEvent>(
        replay = EVENT_BUFFER,
        extraBufferCapacity = EVENT_BUFFER,
    )

    /**
     * Raw discovery-side events.
     * Never use this directly, except from [NearbyConnectionsScanner].
     */
    @InternalConnectionApi
    internal val discoveryEvents = _discoveryEvents.asSharedFlow()

    @Synchronized
    fun startDiscovery() {
        if (discoveryJob?.isActive == true) return

        discoveryJob = scope.launch {
            discoveryService.start().collect { event ->
                track(event)
                _discoveryEvents.emit(event)
            }
        }
    }

    @Synchronized
    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
    }

    @Synchronized
    fun startAdvertising() {
        if (advertisingJob?.isActive == true) return

        advertisingJob = scope.launch {
            advertisingService.start().collect { event ->
                track(event)

                if (event is NearbyConnectionsEvent.Found) {
                    _incomingConnections.emit(event.peer)
                }
            }
        }
    }

    @Synchronized
    fun stopAdvertising() {
        advertisingJob?.cancel()
        advertisingJob = null
    }

    /**
     * Sends [bytes] to [endpointId]. Fails if that peer is not connected.
     */
    suspend fun send(endpointId: String, bytes: ByteArray): Result<Unit> =
        messenger.send(endpointId, bytes)

    /**
     * Only meaningful for peers seen through [incomingConnections]. Call after the user has
     * confirmed the peer's [NearbyConnectionsPeer.authenticationDigits].
     */
    fun accept(endpointId: String) = advertisingService.accept(endpointId)

    fun reject(endpointId: String) = advertisingService.reject(endpointId)

    fun disconnect(endpointId: String) = messenger.disconnect(endpointId)

    /**
     * Only messages from [endpointId].
     */
    fun messages(endpointId: String): Flow<NearbyConnectionsMessage> = messages
        .distinctUntilChangedBy { it.endpointId }
        .filter { it.endpointId == endpointId }

    /**
     * Folds one datasource event into the shared peer/message state. Both radios feed this, so it
     * must stay origin-agnostic - anything that depends on which side opened the connection is
     * handled by the caller.
     */
    private suspend fun track(event: NearbyConnectionsEvent) {
        when (event) {
            NearbyConnectionsEvent.Idle -> Unit

            is NearbyConnectionsEvent.Error ->
                Timber.e(event.e, "Nearby Connections error")

            is NearbyConnectionsEvent.Found -> peers.update { current ->
                current + (event.peer.endpointId to PeerState(event.peer, isConnected = false))
            }

            is NearbyConnectionsEvent.Connected -> peers.update { current ->
                val known = current[event.id] ?: return@update current
                current + (event.id to known.copy(isConnected = true))
            }

            is NearbyConnectionsEvent.Message -> messages.emit(event.message)

            is NearbyConnectionsEvent.Disconnected -> peers.update { it - event.id }

            is NearbyConnectionsEvent.PeerError -> {
                Timber.e("Peer %s failed with status %d", event.id, event.errorCode)
                peers.update { it - event.id }
            }
        }
    }

    /**
     * A peer is remembered from the moment it is seen, but only counts as connected once Nearby
     * says the handshake finished.
     */
    private data class PeerState(
        val peer: NearbyConnectionsPeer,
        val isConnected: Boolean,
    )

    private companion object {
        const val EVENT_BUFFER = 64
    }
}
