package com.fserver.core.network.device.impl.mapper

import com.fserver.core.network.info.model.PeerLocator
import com.fserver.net.spi.TransportEndpoint
import com.fserver.net.transport.android.spi.ip.DirectIpEndpoint
import com.fserver.net.transport.android.spi.multicastdns.MulticastDnsTransportEndpoint
import com.fserver.net.transport.android.spi.nearbyconnection.NearbyConnectionsTransportEndpoint

fun TransportEndpoint.asPeerLocator(): PeerLocator = when (this) {
    is DirectIpEndpoint -> PeerLocator.Ip(
        host = host,
        port = port,
    )

    is MulticastDnsTransportEndpoint -> PeerLocator.Ip(
        host = host,
        port = port,
    )

    is NearbyConnectionsTransportEndpoint -> PeerLocator.NearbyEndpoint(
        endpointId = endpointId,
    )

    else -> error("Unknown transport endpoint type ${this::class.simpleName}")
}
