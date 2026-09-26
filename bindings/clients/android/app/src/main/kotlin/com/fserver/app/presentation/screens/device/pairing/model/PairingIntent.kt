package com.fserver.app.presentation.screens.device.pairing.model

import com.fserver.core.network.auth.AuthMethod

sealed interface PairingIntent {
    data object Connect : PairingIntent

    data class MethodSelected(val method: AuthMethod) : PairingIntent

    data class UpdateSecret(val secret: String) : PairingIntent

    data class SecretDigitPressed(val digit: Char) : PairingIntent

    data object SecretBackspacePressed : PairingIntent
}
