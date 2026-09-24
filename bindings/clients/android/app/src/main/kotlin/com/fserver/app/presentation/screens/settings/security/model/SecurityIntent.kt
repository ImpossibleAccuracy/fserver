package com.fserver.app.presentation.screens.settings.security.model

sealed interface SecurityIntent {
    data class DiscoverableChanged(val enabled: Boolean) : SecurityIntent
    data class DiscoveryChanged(val enabled: Boolean) : SecurityIntent
    data class CodeComparisonChanged(val enabled: Boolean) : SecurityIntent
    data class ServerPasswordChanged(val enabled: Boolean) : SecurityIntent
    data class PinChanged(val enabled: Boolean) : SecurityIntent
    data class BiometricChanged(val enabled: Boolean) : SecurityIntent

    data class ServerPasswordSet(val password: String) : SecurityIntent

    data object WarningDismissed : SecurityIntent
}
