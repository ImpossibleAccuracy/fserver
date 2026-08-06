package com.fserver.app.presentation.screens.files.list

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.filesListEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Files.List> {
        FilesScreen(
            navigateToTransfers = { navigator.navigate(Destination.Transfers) },
            navigateToPicker = { navigator.navigate(Destination.Files.Picker) },
        )
    }
}
