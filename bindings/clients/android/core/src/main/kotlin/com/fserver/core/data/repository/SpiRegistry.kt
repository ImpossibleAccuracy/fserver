package com.fserver.core.data.repository

import com.fserver.net.spi.DiscoveryProvider
import com.fserver.net.spi.SpiId
import com.fserver.net.transport.android.spi.multicastdns.MulticastDnsSPI
import com.fserver.net.transport.android.spi.multicastdns.MulticastDnsScanParams
import com.fserver.net.transport.android.spi.nearbyconnection.NearbyConnectionsSPI
import com.fserver.net.transport.android.spi.nearbyconnection.NearbyConnectionsScanParams

data object SpiRegistry {
    fun findAutomaticScanParams(spiId: SpiId): DiscoveryProvider.ScanParams? = when (spiId) {
        NearbyConnectionsSPI.ID -> NearbyConnectionsScanParams
        MulticastDnsSPI.ID -> MulticastDnsScanParams
        else -> null
    }
}
