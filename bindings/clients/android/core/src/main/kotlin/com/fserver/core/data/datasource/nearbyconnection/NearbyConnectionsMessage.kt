package com.fserver.core.data.datasource.nearbyconnection

internal class NearbyConnectionsMessage(
    val endpointId: String,
    val messageId: Long,
    val message: ByteArray,
)
