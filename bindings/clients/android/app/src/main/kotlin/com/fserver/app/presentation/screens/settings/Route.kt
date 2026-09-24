package com.fserver.app.presentation.screens.settings

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.settingsEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Settings> {
        SettingsScreen(
            navigateToMyDevice = { navigator.navigate(Destination.Settings.MyDevice) },
            navigateToDevices = { navigator.navigate(Destination.Settings.Devices) },
            navigateToSecurity = { navigator.navigate(Destination.Settings.Security) },
            navigateToDiagnostics = { navigator.navigate(Destination.Diagnostics) },
            navigateToAbout = { navigator.navigate(Destination.Settings.About) },
        )
    }
}
