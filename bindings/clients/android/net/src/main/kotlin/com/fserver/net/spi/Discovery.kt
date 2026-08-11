package com.fserver.net.spi

import kotlinx.coroutines.flow.Flow

/** Identifies a discovery provider. Discovery is a separate role from transport: mDNS finds, TCP carries. */
@JvmInline
value class DiscoveryId(val value: String)

/**
 * What a scan was asked to do. Providers declare what they can serve through
 * [DiscoveryProvider.accepts], so adding a provider never means editing a `when` in `:net`.
 */
interface ScanParams

/** Run whatever the named provider does on its own - mDNS, Nearby, a subnet sweep. */
// data class ScanByDiscovery(val discoveryId: DiscoveryId) : ScanParams

/** The user typed an address. */
// data class ScanByAddress(val host: String, val port: Int? = null) : ScanParams

/** The user scanned a code. */
// data class ScanByCode(val payload: String) : ScanParams

/**
 * What discovery knows about a peer before anything is connected: where it is, plus whatever it
 * advertised about itself. Values in [attributes] are hints for the UI, never authorization.
 */
data class DiscoveredEndpoint(
    val endpoint: TransportEndpoint,
    val advertisedName: String,
    val attributes: Map<String, String> = emptyMap(),
    /** Out-of-band code the user has to compare, when the transport has one. */
    val confirmationCode: String? = null,
)

sealed interface PeerEvent {
    data class Appeared(val peer: DiscoveredEndpoint) : PeerEvent

    /** Keyed by [TransportEndpoint.address] - the peer may have never been described. */
    data class Disappeared(val endpointAddress: String) : PeerEvent

    data class Failed(val cause: Throwable) : PeerEvent
}

interface DiscoveryProvider {
    val id: DiscoveryId

    fun accepts(params: ScanParams): Boolean

    /**
     * Runs the scan. Continuous providers (mDNS) keep the flow open and report peers going away;
     * one-shot providers (a typed address) complete.
     */
    fun scan(params: ScanParams): Flow<PeerEvent>
}

/** What this device tells the network about itself. Built by `:net` from the local identity. */
data class Advertisement(
    val deviceId: String,
    val displayName: String,
    val attributes: Map<String, String> = emptyMap(),
)

sealed interface AdvertisingEvent {
    data object Started : AdvertisingEvent
    data object Stopped : AdvertisingEvent
    data class Failed(val cause: Throwable) : AdvertisingEvent
}

interface Advertiser {
    val id: DiscoveryId
    fun advertise(payload: Advertisement): Flow<AdvertisingEvent>
}
