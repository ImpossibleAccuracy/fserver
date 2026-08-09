package com.fserver.app.presentation.screens.settings

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.settingsEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Settings> {
        SettingsScreen(
            navigateToDevices = { navigator.navigate(Destination.Connect) },
            // TODO: the trusted-fingerprint list has no screen yet.
            navigateToTrustedFingerprints = {},
            navigateToDiagnostics = { navigator.navigate(Destination.Diagnostics) },
        )
    }
}
