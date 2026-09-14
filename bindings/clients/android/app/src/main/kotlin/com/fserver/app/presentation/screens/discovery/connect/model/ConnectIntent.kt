package com.fserver.app.presentation.screens.discovery.connect.model

import com.fserver.core.network.TransportKind

sealed interface ConnectIntent {
    /** A device the user has met before: connected now, or trusted and waiting to be dialled. */
    data class KnownDeviceClicked(val deviceId: String) : ConnectIntent

    data class DiscoveredDeviceClicked(val deviceId: String) : ConnectIntent

    data object MethodsClicked : ConnectIntent
    data object MethodsDismissed : ConnectIntent

    /** Starts a ready method, or stops it when it is the one already running. */
    data class MethodToggled(val method: TransportKind) : ConnectIntent

    /** A method that cannot run yet: opens what it is still waiting on. */
    data class MethodClicked(val method: TransportKind) : ConnectIntent
    data object MethodSetupDismissed : ConnectIntent
}
