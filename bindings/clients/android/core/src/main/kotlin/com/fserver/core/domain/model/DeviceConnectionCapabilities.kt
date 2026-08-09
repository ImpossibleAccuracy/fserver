package com.fserver.core.domain.model


/**
 * The connection capabilities of a device, as reported by the device itself.
 */
data class DeviceConnectionCapabilities(
    val tlsVersion: TLSVersion,
    val protocolVersion: ProtocolVersion,
    val access: Access,
    val fingerprints: List<Fingerprint>
) {
    enum class TLSVersion {
        TLS_1_0,
        TLS_1_1,
        TLS_1_2,
        TLS_1_3
    }

    enum class ProtocolVersion {
        HTTP_1_1,
        HTTP_2,
        HTTP_3
    }

    /**
     * How the server gates connections.
     */
    enum class Access {
        Open,
        Password,
        Key,
    }

    data class Fingerprint(
        val key: String,
    )
}
