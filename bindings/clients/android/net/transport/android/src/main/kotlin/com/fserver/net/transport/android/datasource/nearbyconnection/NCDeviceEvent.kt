package com.fserver.net.transport.android.datasource.nearbyconnection

internal sealed interface NCAdvertiserEvent {
    data object Idle : NCAdvertiserEvent
    data object Registered : NCAdvertiserEvent
    data class Error(val error: Exception) : NCAdvertiserEvent

    data object Closed : NCAdvertiserEvent
}

internal sealed interface NCDiscoveryEvent {
    data object Registered : NCDiscoveryEvent

    data class Error(val error: Exception) : NCDiscoveryEvent
}

internal sealed interface NCDeviceEvent : NCAdvertiserEvent, NCDiscoveryEvent {
    data class Found(val peer: NearbyConnectionsPeer) : NCDeviceEvent
    data class Connected(val id: String) : NCDeviceEvent
    data class Message(val message: NearbyConnectionsMessage) : NCDeviceEvent
    data class PeerError(val id: String, val errorCode: Int) : NCDeviceEvent
    data class Disconnected(val endpointId: String) : NCDeviceEvent
}
