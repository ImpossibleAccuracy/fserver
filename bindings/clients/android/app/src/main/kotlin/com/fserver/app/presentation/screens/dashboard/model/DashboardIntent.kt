package com.fserver.app.presentation.screens.dashboard.model

import android.net.Uri

sealed interface DashboardIntent {
    /** The network banner was tapped while it was the one about a network that cannot be named. */
    data object NetworkWarningClicked : DashboardIntent

    /** Files picked in the system dialog, and the device picked for them. */
    data class SendFiles(val deviceId: String, val uris: List<Uri>) : DashboardIntent
}
