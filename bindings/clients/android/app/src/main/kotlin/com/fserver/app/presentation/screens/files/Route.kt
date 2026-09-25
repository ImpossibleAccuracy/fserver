package com.fserver.app.presentation.screens.files

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.filesEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Files> { key ->
        FilesScreen(
            key = key,
            navigateToSourcePick = {
                navigator.navigate(Destination.Source.Setup.Pick())
            },
            navigateUp = { navigator.navigateUp() },
        )
    }
}
