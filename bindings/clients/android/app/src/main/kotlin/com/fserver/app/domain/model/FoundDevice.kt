package com.fserver.app.domain.model

/**
 * A peer discovery turned up, whatever method found it.
 */
data class FoundDevice(
    val id: String,
    val name: String,
    val kind: Kind,
    val source: Source,
) {
    /** What type is this peer. */
    enum class Kind {
        Desktop,
        Laptop,
        Phone,
        Tablet,
        Nas,
        Unknown,
    }

    /**
     * Where the peer was found.
     * This should contain enough information to connect to the peer.
     */
    sealed interface Source {
        data class NetworkServiceDiscovery(
            val serviceName: String,
            val serviceType: String,
            val ipAddress: String,
            val port: Int,
        ) : Source

        data class SubnetScan(
            val ipAddress: String,
            val port: Int,
        ) : Source

        data class ManualEntry(
            val ipAddress: String,
            val port: Int,
        ) : Source

        data class NearbyDevice(
            val deviceId: String,
        ) : Source
    }
}
