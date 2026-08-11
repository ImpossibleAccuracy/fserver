package com.fserver.net.transport.android.spi.nearbyconnection

import com.fserver.net.spi.DiscoveryProvider

/** nearby connections serves pre-selected service id, so there is nothing to configure per scan. */
public data object NearbyConnectionsScanParams : DiscoveryProvider.ScanParams
