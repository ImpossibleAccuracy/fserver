package com.fserver.core.network.info.model

/** A network the device is currently on. */
sealed interface NetworkInfo {
    /** Stable handle for this link, or `null` when the platform withholds it. */
    val id: String?

    /** What to call this link in the UI, or `null` when there is nothing readable to call it. */
    val name: String?

    val capabilities: Set<NetworkCapability>

    /**
     * [ssid] and [bssid] are `null` when the location gate has not been passed: Android answers
     * such a read with a redacted placeholder, and a placeholder shown as a name reads as a bug.
     */
    data class WiFi(
        val ssid: String?,
        val bssid: String?,
    ) : NetworkInfo {
        override val id: String? = bssid
        override val name: String? = ssid
        override val capabilities: Set<NetworkCapability> = LAN_CAPABILITIES
    }

    /**
     * [networkType] is the radio generation, and usually `null`: reading it needs
     * `READ_PHONE_STATE` from API 30, which this module does not ask for.
     */
    data class Mobile(
        val carrierName: String?,
        val networkType: String?,
    ) : NetworkInfo {
        override val id: String? = carrierName
        override val name: String? = carrierName ?: networkType

        /**
         * Nothing beyond a route: carrier NAT puts every subscriber behind a shared address and
         * blocks peer-to-peer traffic inside the block, so sweeping or multicasting it finds
         * nothing while still costing the user data and battery.
         */
        override val capabilities: Set<NetworkCapability> = emptySet()
    }

    /** Ethernet - a LAN like Wi-Fi's, only without a name to report. */
    data object Wired : NetworkInfo {
        override val id: String = "ethernet"
        override val name: String? = null
        override val capabilities: Set<NetworkCapability> = LAN_CAPABILITIES
    }

    /** Online over something this module cannot characterise. A route, and no claims beyond it. */
    data object Other : NetworkInfo {
        override val id: String? = null
        override val name: String? = null
        override val capabilities: Set<NetworkCapability> = emptySet()
    }
}

/** Peers share the link, so they can be swept for and multicast to. */
private val LAN_CAPABILITIES = setOf(
    NetworkCapability.LOCAL_SUBNET,
    NetworkCapability.MULTICAST,
)
