package com.fserver.app.presentation.screens.pairing.model

import com.fserver.core.domain.model.connection.auth.AuthMethod

sealed interface PairingIntent {
    data object Connect : PairingIntent

    data class RememberDeviceChanged(val remember: Boolean) : PairingIntent

    data class MethodSelected(val method: AuthMethod) : PairingIntent

    data class UpdatePassword(val password: String) : PairingIntent
}
