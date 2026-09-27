package com.fserver.net.transport.android.spi.subnet

import android.content.Context
import com.fserver.net.config.SpiContainer
import com.fserver.net.config.SpiFactory
import com.fserver.net.spi.SpiId

/**
 * Discovery only: finds hosts listening on the fixed LAN ports and hands them out as
 * [com.fserver.net.transport.android.spi.ip.DirectIpEndpoint]s, so `DirectIpSPI` has to be
 * installed alongside to dial them.
 */
public data object SubnetScanSPI {
    public val ID: SpiId = SpiId("subnet-scan")

    public fun create(context: Context): SpiFactory {
        val applicationContext = context.applicationContext

        return SpiFactory {
            SpiContainer(
                transport = null,
                discoveryProvider = SubnetScanDiscoveryProvider(applicationContext),
                advertiser = null,
                advertisedAttributes = emptyMap(),
            )
        }
    }
}
