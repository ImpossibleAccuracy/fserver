package com.fserver.app.presentation.screens.settings.pin

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.settingsPinChangeEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Settings.PinChange> { key ->
        PinChangeScreen(
            key = key,
            navigateUp = navigator::navigateUp,
        )
    }
}
