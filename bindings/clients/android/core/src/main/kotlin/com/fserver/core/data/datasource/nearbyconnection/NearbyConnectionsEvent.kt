package com.fserver.core.data.datasource.nearbyconnection

internal sealed interface NearbyConnectionsEvent {
    data object Idle : NearbyConnectionsEvent
    data class Error(val e: Exception) : NearbyConnectionsEvent

    data class Found(val peer: NearbyConnectionsPeer) : NearbyConnectionsEvent
    data class Connected(val id: String) : NearbyConnectionsEvent
    data class Message(val message: NearbyConnectionsMessage) : NearbyConnectionsEvent
    data class Disconnected(val id: String) : NearbyConnectionsEvent
    data class PeerError(val id: String, val errorCode: Int) : NearbyConnectionsEvent
}
