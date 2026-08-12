package com.fserver.app.presentation.screens.pairing.model

sealed interface PairingUiEffect {
    data object NavigateFiles : PairingUiEffect
}
