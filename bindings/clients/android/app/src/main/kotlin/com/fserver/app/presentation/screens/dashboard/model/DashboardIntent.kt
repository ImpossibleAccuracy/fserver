package com.fserver.app.presentation.screens.dashboard.model

sealed interface DashboardIntent {
    data class DeviceExpanded(val deviceId: String) : DashboardIntent
    data object DeviceCollapsed : DashboardIntent
    data object SyncRequestHintDismissed : DashboardIntent

    /** The network banner was tapped while it was the one about a network that cannot be named. */
    data object NetworkWarningClicked : DashboardIntent
}
