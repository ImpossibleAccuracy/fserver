package com.fserver.app.presentation.screens.files

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.filesEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Files> {
        FilesScreen(
            navigateToTransfers = { navigator.navigate(Destination.Transfers) },
        )
    }
}
