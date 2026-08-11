package com.fserver.net.transport.android.spi.multicastdns

import android.content.Context
import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.config.SpiContainer
import com.fserver.net.spi.SpiId
import com.fserver.net.transport.android.datasource.multicastdns.MulticastDnsPortBinder

public data object MulticastDnsSPI {
    val ID: SpiId = SpiId("multicast-dns")

    public fun create(
        context: Context,
        policy: ConnectionPolicy,
    ): SpiContainer {
        val portBinder = MulticastDnsPortBinder()
        val advertiser = MulticastDnsAdvertiser(
            context = context,
            multicastDnsPortBinder = portBinder,
        )
        val discoveryProvider = MulticastDnsDiscoveryProvider(
            context = context
        )

        val transport = MulticastDnsTransport(
            multicastDnsPortBinder = portBinder,
            connectionPolicy = policy,
        )

        return SpiContainer(
            transport = transport,
            discoveryProvider = discoveryProvider,
            advertiser = advertiser,
            advertisedAttributes = emptyMap(),
        )
    }
}
