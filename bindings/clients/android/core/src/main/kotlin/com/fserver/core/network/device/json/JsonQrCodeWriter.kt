package com.fserver.core.network.device.json

import com.fserver.common.model.Fingerprint
import com.fserver.core.network.device.model.KnownRoute
import com.fserver.core.network.device.model.LocalDevice
import kotlinx.serialization.json.Json

/** Writes what [JsonQrCodeParser] reads, for this device's own connection code. */
internal class JsonQrCodeWriter {
    private val json = Json { encodeDefaults = false }

    fun write(
        routes: List<KnownRoute>,
        device: LocalDevice,
        fingerprint: Fingerprint,
    ): String = json.encodeToString(
        QrCodeDto(
            deviceId = device.deviceId,
            name = device.displayName,
            fingerprint = fingerprint.value,
            directIp = routes.filterIsInstance<KnownRoute.Ip>().firstOrNull()?.let {
                QrCodeDto.DirectIp(
                    host = it.host,
                    port = it.port,
                )
            },
            nearby = routes.filterIsInstance<KnownRoute.Nearby>().firstOrNull()?.let {
                QrCodeDto.Nearby(
                    endpointId = it.endpointId
                )
            },
        )
    )
}
