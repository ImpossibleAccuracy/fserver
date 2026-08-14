package com.fserver.core.domain.model

import kotlinx.serialization.Serializable

/** How the caller identified the device it wants to reach - the whole point of a pairing flow. */
@Serializable
sealed interface ConnectionArguments {
    @Serializable
    data class DiscoveredDevice(val id: String) : ConnectionArguments

    @Serializable
    data class Ip(val host: String, val port: Int?) : ConnectionArguments

    @Serializable
    data class QrPayload(val payload: String) : ConnectionArguments
}
