package com.fserver.core.domain.model

/**
 * What a transport can physically do.
 */
enum class NetworkCapability {
    /** Peers share an IP subnet, so their addresses can be enumerated and reached directly. */
    LOCAL_SUBNET,

    /** Multicast and broadcast traffic reaches other peers on the link. */
    MULTICAST,

    /** Any IP route at all — enough to reach a host the user names explicitly. */
    IP_ROUTING,
}
