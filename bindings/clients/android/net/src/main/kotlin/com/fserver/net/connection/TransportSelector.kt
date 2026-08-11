package com.fserver.net.connection

import com.fserver.net.spi.Transport
import com.fserver.net.spi.TransportEndpoint

/** Matches a route to the transport that can carry it, and orders routes by policy. */
internal class TransportSelector(private val transports: List<Transport>) {

    fun forEndpoint(endpoint: TransportEndpoint): Transport? =
        transports.firstOrNull { it.id == endpoint.transport && it.supports(endpoint) }

    fun order(routes: List<PeerRef>, policy: ConnectionPolicy): List<PeerRef> {
        val preference = policy.transportOrder ?: transports.map { it.id }
        return routes.sortedBy { route ->
            preference.indexOf(route.transport).takeIf { it >= 0 } ?: preference.size
        }
    }
}
