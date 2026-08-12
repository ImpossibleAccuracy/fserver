package com.fserver.app.presentation.screens.pairing.model

import androidx.compose.runtime.Immutable
import com.fserver.net.spi.TransportEndpoint
import com.fserver.net.transport.android.spi.ip.DirectIpEndpoint
import kotlinx.serialization.Serializable

/**
 * A device that answered a probe, plus the address it answered on.
 *
 * The address travels with the id because a device reached by typed address or scanned code is
 * in no discovery list: nothing downstream could look up how to reach it again.
 */
@Immutable
data class PairingTarget(
    val deviceId: String,
    val reconnectionArguments: ConnectionArguments?,
) {
    /**
     * Serializable wrapper for [com.fserver.net.spi.TransportEndpoint] to allow it to be sent over navigation.
     */
    @Serializable
    sealed interface ConnectionArguments {
        fun asEndpoint(): TransportEndpoint?

        @Serializable
        data class Ip(val host: String, val port: Int) : ConnectionArguments {
            override fun asEndpoint(): TransportEndpoint = DirectIpEndpoint(host, port)
        }

        companion object {
            fun fromEndpoint(endpoint: TransportEndpoint) = when (endpoint) {
                is DirectIpEndpoint -> Ip(endpoint.host, endpoint.port)
                else -> null
            }
        }
    }
}