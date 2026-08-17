package com.fserver.app.presentation.screens.files.list

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.filesListEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Files.List> {
        FilesScreen(
            navigateToPicker = { navigator.navigate(Destination.Files.Picker) },
        )
    }
}
