package com.fserver.net.transport.android.datasource.nearbyconnection

internal class NearbyConnectionsMessage(
    val endpointId: String,
    val messageId: Long,
    val message: ByteArray,
)
