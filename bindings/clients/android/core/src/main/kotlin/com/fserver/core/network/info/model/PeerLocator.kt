package com.fserver.core.network.info.model

import kotlinx.serialization.Serializable

/**
 * How to find a peer device.
 */
@Serializable
sealed interface PeerLocator {
    /**
     * Device discovered by any detection method, with its ID from the discovery protocol.
     */
    @Serializable
    data class DiscoveredDevice(val id: String) : PeerLocator

    /**
     * Connect by known IP address and port.
     */
    @Serializable
    data class Ip(val host: String, val port: Int?) : PeerLocator

    /**
     * Connect by QR code payload, which encodes a [PeerLocator] in a transport-specific way.
     */
    @Serializable
    data class QrPayload(val payload: String) : PeerLocator
}
