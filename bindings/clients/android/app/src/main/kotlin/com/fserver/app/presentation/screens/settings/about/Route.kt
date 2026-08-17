package com.fserver.app.presentation.screens.settings.about

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.settingsAboutEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Settings.About> {
        AboutScreen(navigateUp = navigator::navigateUp)
    }
}
