package com.fserver.net.connection.impl

import com.fserver.net.config.NetworkConfig
import com.fserver.net.config.NetworkConfigHolder
import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.connection.PeerRef
import com.fserver.net.spi.Transport
import com.fserver.net.spi.TransportEndpoint

/** Matches a route to the transport that can carry it, and orders routes by policy. */
internal class TransportSelector(
    private val configHolder: NetworkConfigHolder<*>,
) {
    private val config: NetworkConfig<*> get() = configHolder.current

    fun forEndpoint(endpoint: TransportEndpoint): Transport? =
        config.transports.firstOrNull { it.id == endpoint.transport && it.supports(endpoint) }

    fun order(routes: List<PeerRef>, policy: ConnectionPolicy): List<PeerRef> {
        val preference = policy.transportOrder ?: config.transports.map { it.id }
        return routes.sortedBy { route ->
            preference.indexOf(route.transport).takeIf { it >= 0 } ?: preference.size
        }
    }
}