package com.fserver.core.network.device.impl

import com.fserver.core.network.device.model.KnownRoute
import com.fserver.core.network.impl.asDetectionMethod
import com.fserver.net.spi.TransportEndpoint
import com.fserver.net.transport.android.spi.ip.DirectIpEndpoint
import com.fserver.net.transport.android.spi.multicastdns.MulticastDnsTransportEndpoint
import com.fserver.net.transport.android.spi.nearbyconnection.NearbyConnectionsTransportEndpoint

/**
 * null for a transport this build has no written form for. Every transport it does know is
 * written down, dialable or not - [KnownRoute.isDialable] carries that on, and a route nobody can
 * open is still a record of where the device was last seen.
 */
internal fun TransportEndpoint.toKnownRoute(): KnownRoute? {
    val method = transport.asDetectionMethod() ?: return null

    return when (this) {
        is DirectIpEndpoint -> KnownRoute.Ip(
            transport = method,
            host = host,
            port = port,
            isDialable = isDialable,
        )

        is MulticastDnsTransportEndpoint -> KnownRoute.Ip(
            transport = method,
            host = host,
            port = port,
            isDialable = isDialable,
        )

        is NearbyConnectionsTransportEndpoint -> KnownRoute.Nearby(endpointId = endpointId)

        else -> null
    }
}
