package com.fserver.core.network.info.model

/** A network the device is currently on. */
sealed interface NetworkInfo {
    val id: String
    val name: String
    val capabilities: Set<NetworkCapability>

    data class WiFi(
        val ssid: String,
        val bssid: String,
    ) : NetworkInfo {
        override val id: String = bssid
        override val name: String = ssid
        override val capabilities: Set<NetworkCapability> = setOf(
            NetworkCapability.LOCAL_SUBNET,
            NetworkCapability.MULTICAST,
        )
    }

    data class Mobile(
        val carrierName: String,
        val networkType: String,
    ) : NetworkInfo {
        override val id: String = carrierName
        override val name: String = networkType

        /**
         * Nothing beyond a route: carrier NAT puts every subscriber behind a shared address and
         * blocks peer-to-peer traffic inside the block, so sweeping or multicasting it finds
         * nothing while still costing the user data and battery.
         */
        override val capabilities: Set<NetworkCapability> = emptySet()
    }
}
