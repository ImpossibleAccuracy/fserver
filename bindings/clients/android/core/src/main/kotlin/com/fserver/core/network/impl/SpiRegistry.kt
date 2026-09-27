package com.fserver.core.network.impl

import com.fserver.core.network.TransportKind
import com.fserver.net.spi.DiscoveryProvider
import com.fserver.net.spi.SpiId
import com.fserver.net.transport.android.spi.ip.DirectIpSPI
import com.fserver.net.transport.android.spi.multicastdns.MulticastDnsSPI
import com.fserver.net.transport.android.spi.multicastdns.MulticastDnsScanParams
import com.fserver.net.transport.android.spi.nearbyconnection.NearbyConnectionsSPI
import com.fserver.net.transport.android.spi.nearbyconnection.NearbyConnectionsScanParams
import com.fserver.net.transport.android.spi.subnet.SubnetScanParams
import com.fserver.net.transport.android.spi.subnet.SubnetScanSPI

/**
 * The only place `:net`'s [SpiId] meets `:core`'s [TransportKind].
 *
 * Internal on purpose: [TransportKind] is public, so carrying the id as a property on it would
 * put a `:net` type on the host's compile classpath and undo the module boundary.
 */
internal data object SpiRegistry {
    /** Null for a kind that has nothing to scan. */
    fun findScanParams(kind: TransportKind): DiscoveryProvider.ScanParams? = when (kind) {
        TransportKind.NearbyConnections -> NearbyConnectionsScanParams
        TransportKind.MulticastDns -> MulticastDnsScanParams
        TransportKind.SubnetScan -> SubnetScanParams()
        TransportKind.ManualAddress -> null
    }
}

internal val TransportKind.spiId: SpiId
    get() = when (this) {
        TransportKind.NearbyConnections -> NearbyConnectionsSPI.ID
        TransportKind.MulticastDns -> MulticastDnsSPI.ID
        TransportKind.SubnetScan -> SubnetScanSPI.ID
        TransportKind.ManualAddress -> DirectIpSPI.ID
    }

internal fun SpiId?.asTransportKind(): TransportKind? = when (this) {
    NearbyConnectionsSPI.ID -> TransportKind.NearbyConnections
    MulticastDnsSPI.ID -> TransportKind.MulticastDns
    SubnetScanSPI.ID -> TransportKind.SubnetScan
    DirectIpSPI.ID -> TransportKind.ManualAddress
    else -> null
}
