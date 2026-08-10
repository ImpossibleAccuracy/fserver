package com.fserver.core.data.repository

import com.fserver.core.data.datasource.multicastdns.MulticastDnsAdvertisingService
import com.fserver.core.data.datasource.multicastdns.MulticastDnsDiscoveryService
import com.fserver.core.data.datasource.multicastdns.MulticastDnsEvent
import com.fserver.core.data.datasource.multicastdns.MulticastDnsPeer
import com.fserver.core.data.detection.scan.MulticastDnsScanner
import com.fserver.core.data.utils.InternalConnectionApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The single seam onto mDNS.
 *
 * Unlike Nearby Connections, mDNS only announces and locates endpoints - a peer listed here is
 * reachable at an address, not connected to. Opening the transport is the caller's job.
 */
internal class MulticastDnsRepository internal constructor(
    private val discoveryService: MulticastDnsDiscoveryService,
    private val advertisingService: MulticastDnsAdvertisingService,
    private val scope: CoroutineScope,
) {
    private var discoveryJob: Job? = null
    private var advertisingJob: Job? = null

    private val peers = MutableStateFlow<Map<String, MulticastDnsPeer>>(emptyMap())

    /**
     * Peers currently announcing themselves on the local network. An entry is replaced, not
     * duplicated, when a peer moves to another address.
     */
    val discoveredDevices: Flow<List<MulticastDnsPeer>> = peers
        .map { it.values.toList() }
        .distinctUntilChanged()

    private val _events = MutableSharedFlow<MulticastDnsEvent>(
        replay = EVENT_BUFFER,
        extraBufferCapacity = EVENT_BUFFER,
    )

    /**
     * Raw events.
     * Never use this directly, except from [MulticastDnsScanner].
     */
    @InternalConnectionApi
    internal val events = _events.asSharedFlow()

    // TODO: add unified launcher for advertising and discovery for all datasources
    @InternalConnectionApi
    fun startBoth() {
        startAdvertising()
        startDiscovery()
    }

    @Synchronized
    fun startDiscovery() {
        if (discoveryJob?.isActive == true) return

        discoveryJob = scope.launch {
            discoveryService.start().collect { event ->
                track(event)
            }
        }
    }

    @Synchronized
    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null

        // Nothing refreshes these once discovery is down, and a stale address is worse than none.
        peers.value = emptyMap()
    }

    @Synchronized
    fun startAdvertising() {
        if (advertisingJob?.isActive == true) return

        advertisingJob = scope.launch {
            advertisingService.start().collect { event ->
                track(event)
            }
        }
    }

    @Synchronized
    fun stopAdvertising() {
        advertisingJob?.cancel()
        advertisingJob = null
    }

    fun findDevice(deviceId: String) = peers.value[deviceId]

    /**
     * Folds one datasource event into the shared peer state. Both discovery and advertising feed
     * this, so it must stay origin-agnostic.
     */
    private suspend fun track(event: MulticastDnsEvent) {
        when (event) {
            MulticastDnsEvent.Idle,
            MulticastDnsEvent.Scanning,
            MulticastDnsEvent.Closed -> Unit

            is MulticastDnsEvent.Error ->
                Timber.e("mDNS error, code=%d", event.errorCode)

            is MulticastDnsEvent.Found -> peers.update { current ->
                current + (event.peer.id to event.peer)
            }

            is MulticastDnsEvent.Disconnected -> peers.update { it - event.id }

            is MulticastDnsEvent.PeerError -> {
                Timber.e("Peer %s failed with code %d", event.id, event.errorCode)
                peers.update { it - event.id }
            }
        }

        _events.emit(event)
    }

    private companion object {
        const val EVENT_BUFFER = 64
    }
}
