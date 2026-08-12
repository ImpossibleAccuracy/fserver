package com.fserver.app.presentation.screens.pairing.model

sealed interface PairingIntent {
    data object Connect : PairingIntent

    data class RememberDeviceChanged(val remember: Boolean) : PairingIntent

    data class PasswordChanged(val password: String) : PairingIntent
}
