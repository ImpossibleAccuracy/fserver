package com.fserver.net.spi

data class SpiContainer(
    val transport: Transport,
    val discoveryProvider: DiscoveryProvider?,
    val advertiser: Advertiser?,
    val advertisedAttributes: Map<String, String>,
)
