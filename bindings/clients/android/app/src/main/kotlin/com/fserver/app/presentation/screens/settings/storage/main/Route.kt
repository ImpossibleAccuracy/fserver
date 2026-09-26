package com.fserver.app.presentation.screens.settings.storage.main

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.settingsStorageEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Settings.Storage> {
        StorageScreen(
            navigateToSource = { navigator.navigate(Destination.Settings.StorageSource(it)) },
            navigateUp = navigator::navigateUp,
        )
    }
}
