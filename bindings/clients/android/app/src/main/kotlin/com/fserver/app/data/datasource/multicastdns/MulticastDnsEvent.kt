package com.fserver.app.data.datasource.multicastdns

internal sealed interface MulticastDnsEvent {
    data object Idle : MulticastDnsEvent
    data object Scanning : MulticastDnsEvent
    data class Error(val errorCode: Int) : MulticastDnsEvent
    data object Closed : MulticastDnsEvent

    data class Found(val peer: MulticastDnsPeer) : MulticastDnsEvent
    data class Disconnected(val id: String) : MulticastDnsEvent
    data class PeerError(val id: String, val errorCode: Int) : MulticastDnsEvent
}
