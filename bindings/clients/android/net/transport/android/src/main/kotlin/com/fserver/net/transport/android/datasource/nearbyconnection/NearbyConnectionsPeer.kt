package com.fserver.net.transport.android.datasource.nearbyconnection

/**
 * The other end of a connection Nearby has set up but nobody has accepted yet.
 *
 * [endpointInfo] is the raw advertisement, decoded by [NearbyEndpointInfo] where it is needed -
 * this layer carries it, it does not read it.
 */
internal class NearbyConnectionsPeer(
    val endpointId: String,
    val endpointInfo: ByteArray,
    val authenticationDigits: String,
)
