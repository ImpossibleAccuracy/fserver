package com.fserver.app.presentation.screens.settings.storage

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.settingsStorageEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Settings.Storage> {
        StorageScreen(navigateUp = navigator::navigateUp)
    }
}
