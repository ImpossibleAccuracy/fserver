package com.fserver.app.domain.model

data class FoundDevice(
    val id: String,
    val name: String,
    val source: Source,
) {
    sealed interface Source {
        data class NetworkServiceDiscovery(
            val serviceName: String,
            val serviceType: String,
            val domain: String,
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
