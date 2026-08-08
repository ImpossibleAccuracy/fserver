package com.fserver.app.data.detection.scan.multicast

internal sealed interface MulticastDnsEvent {
    data object Idle : MulticastDnsEvent
    data object Scanning : MulticastDnsEvent
    data object Closed : MulticastDnsEvent
    data class Found(val peer: MulticastDnsPeer) : MulticastDnsEvent
    data class Error(val errorCode: Int) : MulticastDnsEvent
}
