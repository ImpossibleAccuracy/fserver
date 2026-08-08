package com.fserver.app.data.detection.scan.multicast

/**
 * mDNS peer discovered on the network.
 */
internal data class MulticastDnsPeer(
    val name: String,
    val host: String,
    val port: Int,
)
