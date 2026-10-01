package com.fserver.app.presentation.screens.dashboard.model

sealed interface DashboardUiEffect {
    /** Recorded and offered: the peer's answer comes later. */
    data class FilesOffered(val count: Int) : DashboardUiEffect
}
