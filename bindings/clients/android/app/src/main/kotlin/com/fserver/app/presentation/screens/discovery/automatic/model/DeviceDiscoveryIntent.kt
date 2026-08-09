package com.fserver.app.presentation.screens.discovery.automatic.model

import com.fserver.core.domain.model.DetectionMethod

sealed interface DeviceDiscoveryIntent {
    /** Include or drop a method. Only offered for methods that have everything they need. */
    data class MethodToggled(val method: DetectionMethod) : DeviceDiscoveryIntent

    /** Open the sheet explaining what this method is still waiting on. */
    data class MethodClicked(val method: DetectionMethod) : DeviceDiscoveryIntent

    /**
     * Run one method now, mid-search: joining a method that was left out, or retrying one that
     * has finished.
     */
    data class MethodStartRequested(val method: DetectionMethod) : DeviceDiscoveryIntent

    data object MethodSetupDismissed : DeviceDiscoveryIntent

    /** The sheet's grant buttons. */
    data object GrantRequested : DeviceDiscoveryIntent

    data object StartSearchClicked : DeviceDiscoveryIntent

    /** Stop the running scanners. What they found stays on screen. */
    data object StopSearchClicked : DeviceDiscoveryIntent
}
