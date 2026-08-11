package com.fserver.net.transport.android.datasource.nearbyconnection

/**
 * Lifecycle of the advertising registration itself, not of any one peer. Teardown is the flow
 * completing, so there is no event for it.
 */
internal sealed interface NCAdvertiserEvent {
    data object Registered : NCAdvertiserEvent
    data class Error(val error: Exception) : NCAdvertiserEvent
}

/** Lifecycle of the discovery registration itself, not of any one peer. */
internal sealed interface NCDiscoveryEvent {
    data object Registered : NCDiscoveryEvent
    data class Error(val error: Exception) : NCDiscoveryEvent

    /** In radio range and advertising our service id. Nothing is connected yet. */
    class EndpointFound(
        val endpointId: String,
        /** The raw advertisement; [NearbyEndpointInfo] decodes it. */
        val endpointInfo: ByteArray,
    ) : NCDeviceEvent, NCDiscoveryEvent

    data class EndpointLost(val endpointId: String) : NCDeviceEvent, NCDiscoveryEvent
}

/**
 * What happens to individual endpoints. Kept apart from the two registration lifecycles above
 * because Nearby's connection state is per process, not per scan: these outlive whichever flow
 * was being collected when they were raised, so they travel on
 * [NearbyConnectionsRepository.events] instead.
 */
internal sealed interface NCDeviceEvent {
    /**
     * Both sides have agreed to talk and Nearby has produced the digits, but neither has accepted
     * yet. [incoming] is false when this device dialled.
     */
    data class ConnectionInitiated(
        val peer: NearbyConnectionsPeer,
        val incoming: Boolean,
    ) : NCDeviceEvent

    data class Connected(val endpointId: String) : NCDeviceEvent

    data class ConnectionFailed(val endpointId: String, val statusCode: Int) : NCDeviceEvent

    data class Disconnected(val endpointId: String) : NCDeviceEvent
}
