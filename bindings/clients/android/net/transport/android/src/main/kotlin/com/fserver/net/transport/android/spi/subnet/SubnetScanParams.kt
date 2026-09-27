package com.fserver.net.transport.android.spi.subnet

import com.fserver.net.spi.DiscoveryProvider
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * @param connectTimeout Per host: a LAN peer answers well within it, and every silent address costs
 * this much of one [parallelism] slot.
 * @param parallelism Hosts probed at once.
 */
public data class SubnetScanParams(
    val connectTimeout: Duration = 300.milliseconds,
    val parallelism: Int = 64,
) : DiscoveryProvider.ScanParams {
    init {
        require(connectTimeout.isPositive()) { "connectTimeout must be positive" }
        require(parallelism > 0) { "parallelism must be positive" }
    }
}
