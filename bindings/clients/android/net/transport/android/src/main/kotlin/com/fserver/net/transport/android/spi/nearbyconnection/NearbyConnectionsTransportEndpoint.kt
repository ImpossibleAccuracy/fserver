package com.fserver.net.transport.android.spi.nearbyconnection

import com.fserver.net.spi.SpiId
import com.fserver.net.spi.TransportEndpoint
import com.fserver.net.transport.android.datasource.nearbyconnection.NearbyConnectionsPeer

internal class NearbyConnectionsTransportEndpoint(
    val peer: NearbyConnectionsPeer,
) : TransportEndpoint {
    override val transport: SpiId = NearbyConnectionsSPI.ID
    override val address: String = peer.endpointId
}
