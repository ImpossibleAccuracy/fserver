package com.fserver.net.transport.android.spi.multicastdns

import com.fserver.net.spi.SpiId
import com.fserver.net.spi.TransportEndpoint

/**
 * @param isDialable false for endpoints built from an accepted socket: the port seen there is the
 * peer's ephemeral source port, not the one it listens on, so it cannot be connected back to.
 */
@ConsistentCopyVisibility
internal data class MulticastDnsTransportEndpoint internal constructor(
    val host: String,
    val port: Int,
    val isDialable: Boolean = true,
) : TransportEndpoint {
    override val transport: SpiId = MulticastDnsSPI.ID

    // Kept distinct from a dialable address so an inbound route never de-dups with a discovered one.
    override val address: String = if (isDialable) "$host:$port" else "$host:$port/inbound"
}
