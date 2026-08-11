package com.fserver.net.transport.android.datasource.multicastdns

import com.fserver.net.transport.android.spi.multicastdns.MulticastDnsTransportEndpoint

/**
 * mDNS peer discovered on the network.
 *
 * @param attributes what the peer put in its TXT record. Descriptive only - never authorization.
 */
internal data class MulticastDnsPeer(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val attributes: Map<String, String>,
) {
    fun asEndpoint() = MulticastDnsTransportEndpoint(
        host = host,
        port = port,
    )
}
