package com.fserver.core.network.device.json

import kotlinx.serialization.Serializable

@Serializable
internal data class QrCodeDto(
    val deviceId: String? = null,
    val name: String? = null,
    val fingerprint: String? = null,
    val directIp: DirectIp? = null,
    val nearby: Nearby? = null,
) {
    init {
        require(directIp != null || nearby != null)
    }

    @Serializable
    data class DirectIp(
        val host: String,
        val port: Int?,
    )

    @Serializable
    data class Nearby(
        val endpointId: String,
    )
}
