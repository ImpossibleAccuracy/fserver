package com.fserver.app.presentation.screens.settings.security

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.settingsSecurityEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Settings.Security> {
        SecurityScreen(
            navigateToPinChange = { navigator.navigate(Destination.Settings.PinChange()) },
            navigateToPinSetup = {
                navigator.navigate(Destination.Settings.PinChange(enableOnSave = true))
            },
            navigateToOneTimeCode = { navigator.navigate(Destination.Settings.OneTimeCode) },
            navigateUp = navigator::navigateUp,
        )
    }
}
