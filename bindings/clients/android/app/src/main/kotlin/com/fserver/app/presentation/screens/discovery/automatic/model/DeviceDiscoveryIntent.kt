package com.fserver.app.presentation.screens.discovery.automatic.model

import com.fserver.core.network.TransportKind

sealed interface DeviceDiscoveryIntent {
    data class MethodToggled(val method: TransportKind) : DeviceDiscoveryIntent
    data class MethodClicked(val method: TransportKind) : DeviceDiscoveryIntent
    data object MethodSetupDismissed : DeviceDiscoveryIntent

    data class MethodStartRequested(val method: TransportKind) : DeviceDiscoveryIntent

    data object StartSearchClicked : DeviceDiscoveryIntent
    data object StopSearchClicked : DeviceDiscoveryIntent
}
