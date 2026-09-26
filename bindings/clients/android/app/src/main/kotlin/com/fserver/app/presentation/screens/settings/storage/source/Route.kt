package com.fserver.app.presentation.screens.settings.storage.source

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.settingsStorageSourceEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Settings.StorageSource> { key ->
        StorageSourceScreen(
            key = key,
            navigateUp = navigator::navigateUp,
        )
    }
}
