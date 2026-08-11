package com.fserver.net.spi

import kotlinx.coroutines.flow.Flow

/** Makes this device findable by whatever a [DiscoveryProvider] on the other side is scanning. */
interface Advertiser {
    val id: DiscoveryProvider.Id

    fun advertise(payload: Payload): Flow<Event>

    /** What this device tells the network about itself. Built by `:net` from the local identity. */
    data class Payload(
        val deviceId: String,
        val displayName: String,
        val attributes: Map<String, String> = emptyMap(),
    )

    sealed interface Event {
        data object Started : Event
        data object Stopped : Event
        data class Failed(val cause: Throwable) : Event
    }
}