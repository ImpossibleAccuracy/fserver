package com.fserver.core.network.device.model

/**
 * A peer mid-handshake, waiting on this device's answer to an out-of-band code comparison.
 */
data class PendingConfirmation(
    val deviceId: String,
    val codeGroups: List<String>,
)
