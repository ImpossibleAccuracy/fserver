package com.fserver.app.presentation.screens.files.list

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.filesListEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Files.List> {
        FilesScreen(
            navigateToActions = { navigator.navigate(Destination.Files.Actions) },
            navigateToConnect = { navigator.navigate(Destination.Connect) },
            navigateToSourcePick = { navigator.navigate(Destination.Source.Pick) },
            navigateToSyncRequest = { navigator.navigate(Destination.SyncRequest.Details(it)) },
        )
    }
}
