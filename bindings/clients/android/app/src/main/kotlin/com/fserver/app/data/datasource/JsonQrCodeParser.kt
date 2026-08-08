package com.fserver.app.data.datasource

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Reads the MVP code format: `{"ip":"192.168.1.42","port":8384}`.
 */
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

        if (dto.ip.isBlank()) return null
        if (dto.port != null && dto.port !in PORT_RANGE) return null

        return dto
    }

    @Serializable
    data class QrCodeDto(
        val ip: String,
        val port: Int? = null,
    )

    private companion object {
        val PORT_RANGE = 1..65535
    }
}
