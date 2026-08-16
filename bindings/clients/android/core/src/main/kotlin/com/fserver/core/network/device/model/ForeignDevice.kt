package com.fserver.core.network.device.model

import com.fserver.core.network.info.DetectionMethod
import java.time.Instant

@ConsistentCopyVisibility
data class ForeignDevice internal constructor(
    val deviceId: String,
    val displayName: String,
    val kind: DeviceKind?,
    val routes: List<DeviceRoute>,
    val foundBy: DetectionMethod?,
    val lastSeen: Instant,
    val handshake: Handshake?,
    val hasSession: Boolean,
) {
    /** What a completed handshake proved and agreed on. Absent until one has run. */
    data class Handshake(
        /** Human-comparable form of the peer's proven public key, in space-separated groups. */
        val fingerprint: String,
        val protocolVersion: Int,
        val cipherSuite: String,
    )

    /** One way to reach the device, as discovered by the local machine. */
    data class DeviceRoute(
        val address: String,
        val foundBy: DetectionMethod?,
    )
}
