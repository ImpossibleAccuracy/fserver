package com.fserver.net.spi

import com.fserver.net.security.identity.LocalIdentity
import kotlinx.coroutines.flow.Flow

/** Makes this device findable by whatever a [DiscoveryProvider] on the other side is scanning. */
interface Advertiser {
    val id: SpiId

    /** Suspends only to prepare the advertisement; the flow carries what happens after. */
    suspend fun advertise(payload: Payload): Flow<Event>

    /**
     * What this device tells the network about itself. Built by `:net` from the local identity.
     *
     * @param identity *This* device's identity.
     * @param essential Advertised whatever the budget: what a peer needs to tell one device from another.
     * @param optional Advertised when the transport has room, dropped without notice when it does not.
     */
    data class Payload(
        val identity: LocalIdentity,
        val essential: Map<String, String> = emptyMap(),
        val optional: Map<String, String> = emptyMap(),
    ) {
        /** The whole advertisement, for transport with no practical limit. */
        val attributes: Map<String, String> get() = essential + optional
    }

    sealed interface Event {
        data object Started : Event
        data object Stopped : Event
        data class Failed(val cause: Throwable) : Event
    }
}
