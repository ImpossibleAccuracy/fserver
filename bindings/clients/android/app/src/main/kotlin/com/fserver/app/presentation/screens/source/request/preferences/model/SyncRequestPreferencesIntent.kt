package com.fserver.app.presentation.screens.source.request.preferences.model

sealed interface SyncRequestPreferencesIntent {
    data class WifiOnlyToggled(val enabled: Boolean) : SyncRequestPreferencesIntent

    data class ChargingOnlyToggled(val enabled: Boolean) : SyncRequestPreferencesIntent

    data class LimitFilesToggled(val enabled: Boolean) : SyncRequestPreferencesIntent

    data class MaxFilesStepped(val steps: Int) : SyncRequestPreferencesIntent

    data class LimitSizeToggled(val enabled: Boolean) : SyncRequestPreferencesIntent

    data class MaxSizeStepped(val steps: Int) : SyncRequestPreferencesIntent

    data object Accepted : SyncRequestPreferencesIntent
}
