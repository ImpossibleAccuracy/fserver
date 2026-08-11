package com.fserver.net.spi

import kotlinx.coroutines.flow.Flow

/**
 * Finds peers. A separate role from [Transport]: mDNS finds, TCP carries.
 */
interface DiscoveryProvider {
    val id: SpiId

    fun accepts(params: ScanParams): Boolean

    /**
     * Runs the scan. Continuous providers (mDNS) keep the flow open and report peers going away;
     * one-shot providers (a typed address) complete.
     */
    fun scan(params: ScanParams): Flow<Event>

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
 *
 * @param endpoint Where to connect to the peer.
 * @param advertisedName The name the peer advertised for itself (visible name).
 * @param attributes Arbitrary key/value pairs the peer advertised for itself.
 * @param confirmationCode Out-of-band code the user has to compare, when the transport has one.
 */
data class DiscoveredEndpoint(
    val endpoint: TransportEndpoint,
    val advertisedName: String,
    val attributes: Map<String, String> = emptyMap(),
    val confirmationCode: String? = null,
)
