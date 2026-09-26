package com.fserver.app.presentation.screens.settings.onetimecode

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.settingsOneTimeCodeEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Settings.OneTimeCode> {
        OneTimeCodeScreen(
            navigateUp = navigator::navigateUp,
        )
    }
}
