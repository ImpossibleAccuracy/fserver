package com.fserver.core.network.device.json

import com.fserver.core.Constants
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Reads the MVP code format - see [QrCodeDto] for what a code carries. */
internal class JsonQrCodeParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(payload: String): QrCodeDto? {
        val dto = try {
            json.decodeFromString<QrCodeDto>(payload)
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }

        if (!validate(dto)) return null
        return dto
    }

    private fun validate(dto: QrCodeDto): Boolean {
        if (dto.directIp != null) {
            if (dto.directIp.host.isBlank()) return false
            if (dto.directIp.port != null && dto.directIp.port !in Constants.VALID_PORT_RANGE)
                return false
        }

        if (dto.nearby != null) {
            if (dto.nearby.endpointId.isBlank()) return false
        }

        return true
    }
}
