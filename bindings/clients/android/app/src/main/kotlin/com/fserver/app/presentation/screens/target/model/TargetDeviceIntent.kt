package com.fserver.app.presentation.screens.target.model

sealed interface TargetDeviceIntent {
    data class DeviceSelected(val deviceId: String) : TargetDeviceIntent

    data object ConnectRouteOpened : TargetDeviceIntent

    data object ContinueClicked : TargetDeviceIntent
}
