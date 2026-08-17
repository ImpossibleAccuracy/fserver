package com.fserver.net.transport.android.spi.multicastdns

import com.fserver.net.spi.SpiId
import com.fserver.net.spi.TransportEndpoint

public data class MulticastDnsTransportEndpoint(
    val host: String,
    val port: Int,
    override val isDialable: Boolean = true,
) : TransportEndpoint {
    override val transport: SpiId = MulticastDnsSPI.ID

    // Kept distinct from a dialable address so an inbound route never de-dups with a discovered one.
    override val address: String = if (isDialable) "$host:$port" else "$host:$port/inbound"
    override val hostAddress: String = host
}
