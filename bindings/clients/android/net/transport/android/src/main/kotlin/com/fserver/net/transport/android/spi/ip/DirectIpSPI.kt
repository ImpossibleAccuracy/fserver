package com.fserver.net.transport.android.spi.ip

import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.config.SpiContainer
import com.fserver.net.spi.SpiId

public object DirectIpSPI {
    public val ID: SpiId = SpiId("direct-ip")

    public fun create(
        connectionPolicy: ConnectionPolicy,
    ): SpiContainer = SpiContainer(
        transport = DirectIpTransport(connectionPolicy),
        discoveryProvider = null,
        advertiser = null,
        advertisedAttributes = emptyMap(),
    )
}
