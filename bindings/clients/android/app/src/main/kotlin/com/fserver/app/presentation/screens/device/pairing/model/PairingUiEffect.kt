package com.fserver.app.presentation.screens.device.pairing.model

sealed interface PairingUiEffect {
    data object NavigateNext : PairingUiEffect
}
