package com.fserver.app.presentation.screens.settings.pin.model

sealed interface PinChangeIntent {
    data class DigitPressed(val digit: Char) : PinChangeIntent
    data object BackspacePressed : PinChangeIntent
}
