package com.fserver.app.presentation.model

import com.fserver.core.network.device.model.PendingConfirmation

/**
 * A peer mid-handshake, waiting on this device's answer to a code comparison.
 */
data class PendingConfirmationUi(
    val deviceId: String,
    val codeGroups: List<String>,
)

fun PendingConfirmation.toUi(): PendingConfirmationUi = PendingConfirmationUi(
    deviceId = deviceId,
    codeGroups = codeGroups,
)
