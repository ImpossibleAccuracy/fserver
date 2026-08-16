package com.fserver.core.network.impl

import com.fserver.core.network.info.DetectionMethod
import com.fserver.net.spi.DiscoveryProvider
import com.fserver.net.spi.SpiId
import com.fserver.net.transport.android.spi.ip.DirectIpSPI
import com.fserver.net.transport.android.spi.multicastdns.MulticastDnsSPI
import com.fserver.net.transport.android.spi.multicastdns.MulticastDnsScanParams
import com.fserver.net.transport.android.spi.nearbyconnection.NearbyConnectionsSPI
import com.fserver.net.transport.android.spi.nearbyconnection.NearbyConnectionsScanParams

/**
 * The only place `:net`'s [SpiId] meets `:core`'s [DetectionMethod].
 *
 * Internal on purpose: [DetectionMethod] is public, so carrying the id as a property on it would
 * put a `:net` type on the host's compile classpath and undo the module boundary.
 */
internal data object SpiRegistry {
    fun findAutomaticScanParams(spiId: SpiId): DiscoveryProvider.ScanParams? = when (spiId) {
        NearbyConnectionsSPI.ID -> NearbyConnectionsScanParams
        MulticastDnsSPI.ID -> MulticastDnsScanParams
        else -> null
    }
}

internal val DetectionMethod.spiId: SpiId
    get() = when (this) {
        DetectionMethod.Automatic.NearbyConnections -> NearbyConnectionsSPI.ID
        DetectionMethod.Automatic.MulticastDns -> MulticastDnsSPI.ID
        DetectionMethod.OnDemand.SubnetScan -> DirectIpSPI.ID // TODO
        DetectionMethod.OnDemand.ManualAddress -> DirectIpSPI.ID
    }

internal fun SpiId?.asDetectionMethod(): DetectionMethod? = when (this) {
    NearbyConnectionsSPI.ID -> DetectionMethod.Automatic.NearbyConnections
    MulticastDnsSPI.ID -> DetectionMethod.Automatic.MulticastDns
    DirectIpSPI.ID -> DetectionMethod.OnDemand.ManualAddress
    else -> null
}
