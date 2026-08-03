package com.fserver.app.presentation.screens.pairing.model

sealed interface PairingIntent {
    data class RememberDeviceChanged(val remember: Boolean) : PairingIntent
}
