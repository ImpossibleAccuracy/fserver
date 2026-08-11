package com.fserver.net.transport.android.spi.ip

import com.fserver.net.config.SpiContainer
import com.fserver.net.config.SpiFactory
import com.fserver.net.spi.SpiId

public object DirectIpSPI {
    public val ID: SpiId = SpiId("direct-ip")

    public fun create(): SpiFactory = SpiFactory { environment ->
        SpiContainer(
            transport = DirectIpTransport(environment.policy),
            discoveryProvider = null,
            advertiser = null,
            advertisedAttributes = emptyMap(),
        )
    }
}
