package com.fserver.core.network.device.impl.mapper

import com.fserver.core.network.TransportKind
import com.fserver.core.network.device.model.KnownRoute
import com.fserver.net.spi.TransportEndpoint
import com.fserver.net.transport.android.spi.ip.DirectIpEndpoint
import com.fserver.net.transport.android.spi.multicastdns.MulticastDnsTransportEndpoint
import com.fserver.net.transport.android.spi.nearbyconnection.NearbyConnectionsTransportEndpoint

internal fun TransportEndpoint.toKnownRoute(): KnownRoute = when (this) {
    is DirectIpEndpoint -> KnownRoute.Ip(
        transport = TransportKind.ManualAddress,
        host = host,
        port = port,
        isDialable = isDialable,
    )

    is MulticastDnsTransportEndpoint -> KnownRoute.Ip(
        transport = TransportKind.MulticastDns,
        host = host,
        port = port,
        isDialable = isDialable,
    )

    is NearbyConnectionsTransportEndpoint -> KnownRoute.Nearby(endpointId = endpointId)

    else -> error("Unknown transport endpoint type ${this::class.simpleName}")
}
