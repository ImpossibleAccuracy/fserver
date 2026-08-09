package com.fserver.app.data.datasource.nearbyconnection

class NearbyConnectionsMessage(
    val endpointId: String,
    val messageId: Long,
    val message: ByteArray,
)
