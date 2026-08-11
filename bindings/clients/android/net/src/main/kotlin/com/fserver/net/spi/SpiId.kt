package com.fserver.net.spi

/**
 * Identifies an SPI implementation - a [Transport], a [DiscoveryProvider], and the [Advertiser]
 * that pairs with it. Values are chosen by the implementing module.
 */
@JvmInline
value class SpiId(val value: String)
