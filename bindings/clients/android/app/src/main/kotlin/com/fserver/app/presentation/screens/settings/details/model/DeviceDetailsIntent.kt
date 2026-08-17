package com.fserver.app.presentation.screens.settings.details.model

sealed interface DeviceDetailsIntent {
    data object DisconnectClicked : DeviceDetailsIntent
    data object ForgetClicked : DeviceDetailsIntent
}
