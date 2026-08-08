package com.fserver.app.presentation.screens.discovery.automatic.model

sealed interface DeviceDiscoveryIntent {
    /** Restart the automatic detection pass. */
    data object RefreshClicked : DeviceDiscoveryIntent

    /** Escape hatch after the automatic pass found nothing: sweep the local subnet. */
    data object ScanSubnetClicked : DeviceDiscoveryIntent
}
