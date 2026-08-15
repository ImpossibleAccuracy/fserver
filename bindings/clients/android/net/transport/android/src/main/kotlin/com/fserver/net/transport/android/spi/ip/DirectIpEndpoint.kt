package com.fserver.net.transport.android.spi.ip

import com.fserver.net.spi.SpiId
import com.fserver.net.spi.TransportEndpoint

public data class DirectIpEndpoint(
    val host: String,
    val port: Int,
) : TransportEndpoint {
    override val transport: SpiId = DirectIpSPI.ID
    override val address: String = "$host:$port"
    override val hostAddress: String = host
}
