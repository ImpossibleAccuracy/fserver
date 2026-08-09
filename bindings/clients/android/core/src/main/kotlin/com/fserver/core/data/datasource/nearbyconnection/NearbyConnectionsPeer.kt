package com.fserver.core.data.datasource.nearbyconnection

data class NearbyConnectionsPeer(
    val endpointId: String,
    val endpointName: String,
    /**
     * Short token Nearby derives from the connection. Both sides compute the same digits, so
     * showing them to the user and having them compare is what rules out a man-in-the-middle.
     * Meaningless unless a human actually checks it before the connection is accepted.
     */
    val authenticationDigits: String,
)
