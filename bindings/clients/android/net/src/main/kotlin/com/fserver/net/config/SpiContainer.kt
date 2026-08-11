package com.fserver.net.config

import com.fserver.net.spi.Advertiser
import com.fserver.net.spi.DiscoveryProvider
import com.fserver.net.spi.Transport

data class SpiContainer(
    val transport: Transport,
    val discoveryProvider: DiscoveryProvider?,
    val advertiser: Advertiser?,
    val advertisedAttributes: Map<String, String>,
)
