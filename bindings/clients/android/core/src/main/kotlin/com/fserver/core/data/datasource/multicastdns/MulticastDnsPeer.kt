package com.fserver.core.data.datasource.multicastdns

/**
 * mDNS peer discovered on the network.
 */
internal data class MulticastDnsPeer(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
)
