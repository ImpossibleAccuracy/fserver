package com.fserver.app.presentation.screens.device.details.model

sealed interface DeviceDetailsIntent {
    data object DisconnectClicked : DeviceDetailsIntent
    data object Reconnect : DeviceDetailsIntent
    data object ForgetClicked : DeviceDetailsIntent
}
