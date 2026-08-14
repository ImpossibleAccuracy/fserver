package com.fserver.net.connection.throttle

import com.fserver.net.spi.SpiId
import com.fserver.net.spi.TransportEndpoint

/** Where a pre-auth attempt came from. */
internal class HandshakeSource(val transport: SpiId, val endpoint: TransportEndpoint) {
    /** Check equality by transport and address, not the full endpoint. */
    override fun equals(other: Any?): Boolean =
        other is HandshakeSource &&
                transport == other.transport &&
                endpoint.address == other.endpoint.address

    override fun hashCode(): Int = 31 * transport.hashCode() + endpoint.address.hashCode()

    override fun toString(): String = "${transport.value}:${endpoint.address}"
}
