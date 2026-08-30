package com.fserver.app.presentation.screens.target.model

sealed interface TargetDeviceUiEffect {
    data class AnswerDevice(val deviceId: String) : TargetDeviceUiEffect
}
