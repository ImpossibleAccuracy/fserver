package com.fserver.app.presentation.screens.discovery.model

sealed interface DeviceDiscoveryIntent {
    data object RefreshClicked : DeviceDiscoveryIntent
}
