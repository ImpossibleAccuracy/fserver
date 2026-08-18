package com.fserver.core.network.info.model

/**
 * What a transport can physically do.
 */
enum class NetworkCapability {
    /** Peers share an IP subnet, so their addresses can be enumerated and reached directly. */
    LOCAL_SUBNET,

    /** Multicast and broadcast traffic reaches other peers on the link. */
    MULTICAST,
}
