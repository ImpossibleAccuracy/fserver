package com.fserver.app.presentation.screens.source.list

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.syncRequestListEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Source.Request.List> {
        SyncRequestListScreen(
            navigateToDetails = { navigator.navigate(Destination.Source.Request.Details(it)) },
            navigateUp = { navigator.navigateUp() },
        )
    }
}
