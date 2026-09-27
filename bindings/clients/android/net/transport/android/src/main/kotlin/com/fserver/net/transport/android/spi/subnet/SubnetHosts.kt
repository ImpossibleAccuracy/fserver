package com.fserver.net.transport.android.spi.subnet

import java.net.Inet4Address

/** Which addresses a sweep visits. IPv4 only: an IPv6 /64 cannot be enumerated. */
internal object SubnetHosts {
    /** Wider subnets are narrowed to the /24 around this device - a /16 is 65k silent hosts. */
    const val NARROWEST_PREFIX = 24

    /** Hosts of [address]'s subnet, network and broadcast excluded; empty for /31 and /32. */
    fun hosts(address: Inet4Address, prefixLength: Int): List<String> {
        val prefix = prefixLength.coerceAtLeast(NARROWEST_PREFIX)
        if (prefix >= 31) return emptyList()

        val ip = address.address.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
        val mask = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        val network = ip and mask
        val broadcast = network or (mask.inv() and 0xFFFFFFFFL)

        return ((network + 1) until broadcast).map { host ->
            "${host shr 24 and 0xFF}.${host shr 16 and 0xFF}.${host shr 8 and 0xFF}.${host and 0xFF}"
        }
    }
}
