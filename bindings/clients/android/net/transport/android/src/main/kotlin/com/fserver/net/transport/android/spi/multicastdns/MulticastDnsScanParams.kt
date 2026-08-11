package com.fserver.net.transport.android.spi.multicastdns

import com.fserver.net.spi.DiscoveryProvider

/** mDNS browses one fixed service type, so there is nothing to configure per scan. */
internal data object MulticastDnsScanParams : DiscoveryProvider.ScanParams
