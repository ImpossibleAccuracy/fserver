package com.fserver.app.presentation.screens.discovery.automatic.model

import com.fserver.core.domain.model.network.DetectionMethod

sealed interface DeviceDiscoveryIntent {
    data class MethodToggled(val method: DetectionMethod) : DeviceDiscoveryIntent
    data class MethodClicked(val method: DetectionMethod) : DeviceDiscoveryIntent
    data object MethodSetupDismissed : DeviceDiscoveryIntent

    data class MethodStartRequested(val method: DetectionMethod) : DeviceDiscoveryIntent

    data object StartSearchClicked : DeviceDiscoveryIntent
    data object StopSearchClicked : DeviceDiscoveryIntent
}
