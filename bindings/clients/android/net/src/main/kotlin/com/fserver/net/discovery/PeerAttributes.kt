package com.fserver.net.discovery

/**
 * Keys `:net` reads out of [com.fserver.net.spi.DiscoveredEndpoint.attributes] and writes into
 * an advertisement. Transport that uses different keys simply produces peers with less known
 * about them - nothing breaks.
 */
object PeerAttributes {
    const val DEVICE_ID = "did"
    const val DISPLAY_NAME = "name"
    const val KIND = "kind"
    const val PROTOCOL_MIN = "pmin"
    const val PROTOCOL_MAX = "pmax"

    /** Comma-separated auth method ids - the greeting, for peers that cannot ask for one. */
    const val AUTH_METHODS = "auth"

    internal const val SEPARATOR = ","
}
