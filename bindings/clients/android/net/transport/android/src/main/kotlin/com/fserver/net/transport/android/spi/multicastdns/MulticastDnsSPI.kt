package com.fserver.net.transport.android.spi.multicastdns

import android.content.Context
import com.fserver.net.config.SpiContainer
import com.fserver.net.config.SpiFactory
import com.fserver.net.spi.SpiId
import com.fserver.net.transport.android.datasource.multicastdns.MulticastDnsPortBinder

public data object MulticastDnsSPI {
    public val ID: SpiId = SpiId("multicast-dns")

    public fun create(context: Context): SpiFactory {
        val applicationContext = context.applicationContext

        return SpiFactory { environment ->
            val portBinder = MulticastDnsPortBinder()

            SpiContainer(
                transport = MulticastDnsTransport(
                    multicastDnsPortBinder = portBinder,
                    connectionPolicy = environment.policy,
                ),
                discoveryProvider = MulticastDnsDiscoveryProvider(
                    context = applicationContext,
                    identityStore = environment.identityStore,
                ),
                advertiser = MulticastDnsAdvertiser(
                    context = applicationContext,
                    multicastDnsPortBinder = portBinder,
                ),
                advertisedAttributes = emptyMap(),
            )
        }
    }
}
