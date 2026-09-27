package com.fserver.net.config

import com.fserver.net.spi.Advertiser
import com.fserver.net.spi.DiscoveryProvider
import com.fserver.net.spi.Transport

/**
 * One SPI's parts. [transport] is null for a discovery-only SPI, whose endpoints another
 * installed transport dials.
 */
data class SpiContainer(
    val transport: Transport?,
    val discoveryProvider: DiscoveryProvider?,
    val advertiser: Advertiser?,
    val advertisedAttributes: Map<String, String>,
)
