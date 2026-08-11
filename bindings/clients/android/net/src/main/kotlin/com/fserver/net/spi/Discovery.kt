package com.fserver.net.spi

import kotlinx.coroutines.flow.Flow

/**
 * Finds peers. A separate role from [Transport]: mDNS finds, TCP carries.
 */
interface DiscoveryProvider {
    val id: Id

    fun accepts(params: ScanParams): Boolean

    /**
     * Runs the scan. Continuous providers (mDNS) keep the flow open and report peers going away;
     * one-shot providers (a typed address) complete.
     */
    fun scan(params: ScanParams): Flow<Event>

    /** Identifies a discovery provider, and the [Advertiser] that pairs with it. */
    @JvmInline
    value class Id(val value: String)

    /**
     * What a scan was asked to do. Providers declare what they can serve through [accepts].
     */
    interface ScanParams

    sealed interface Event {
        data class Appeared(val peer: DiscoveredEndpoint) : Event

        /** Keyed by [TransportEndpoint.address] - the peer may have never been described. */
        data class Disappeared(val endpointAddress: String) : Event

        data class Failed(val cause: Throwable) : Event
    }
}

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
