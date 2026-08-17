package com.fserver.app.presentation.screens.settings.pin.model

sealed interface PinChangeUiEffect {
    data object NavigateBack : PinChangeUiEffect
}
