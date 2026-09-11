package com.fserver.app.presentation.screens.source.setup.target.model

sealed interface SourceTargetIntent {
    data class DeviceSelected(val deviceId: String) : SourceTargetIntent

    data class ReconnectClicked(val deviceId: String) : SourceTargetIntent

    data object SubnetScanClicked : SourceTargetIntent

    data class DiscoveredDeviceClicked(val deviceId: String) : SourceTargetIntent

    data object Confirmed : SourceTargetIntent
}
