package com.fserver.net.connection

import com.fserver.net.spi.SpiId
import com.fserver.net.spi.TransportEndpoint

/** One way to reach one device. A device found twice has two of these and still one session. */
data class PeerRef(
    val deviceId: String,
    val transport: SpiId,
    val endpoint: TransportEndpoint,
) {
    companion object {
        /**
         * Route to an address typed in or scanned rather than discovered, where the device behind
         * it is unknown until the handshake names it. The session still lands under the real id.
         */
        fun build(endpoint: TransportEndpoint): PeerRef = PeerRef(
            deviceId = UNKNOWN_DEVICE_ID,
            transport = endpoint.transport,
            endpoint = endpoint,
        )

        /** Never matches a registered session, so a connect on it always dials. */
        const val UNKNOWN_DEVICE_ID: String = ""
    }
}