package com.fserver.app.presentation.screens.settings.model

sealed interface SettingsIntent {
    data class WifiOnlyChanged(val enabled: Boolean) : SettingsIntent
    data class CompressChanged(val enabled: Boolean) : SettingsIntent
}
