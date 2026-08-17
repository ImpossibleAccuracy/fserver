package com.fserver.net.transport.android.spi.nearbyconnection

import com.fserver.net.spi.SpiId
import com.fserver.net.spi.TransportEndpoint

/**
 * Nearby's endpoint id is the whole address. It is handed out per advertising session, so it
 * identifies a route and never a device - the device id rides in the advertisement instead.
 */
public data class NearbyConnectionsTransportEndpoint(
    val endpointId: String,
) : TransportEndpoint {
    override val transport: SpiId = NearbyConnectionsSPI.ID
    override val address: String = endpointId
}
