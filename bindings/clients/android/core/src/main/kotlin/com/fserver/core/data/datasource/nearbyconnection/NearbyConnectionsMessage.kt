package com.fserver.core.data.datasource.nearbyconnection

class NearbyConnectionsMessage(
    val endpointId: String,
    val messageId: Long,
    val message: ByteArray,
)
