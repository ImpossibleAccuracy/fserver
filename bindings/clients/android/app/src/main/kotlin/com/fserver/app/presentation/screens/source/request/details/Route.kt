package com.fserver.app.presentation.screens.source.request.details

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.syncRequestDetailsEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Source.Request.Details> { key ->
        SyncRequestDetailsScreen(
            key = key,
            navigateToLocation = {
                navigator.navigate(Destination.Source.Request.Location(key.sourceId))
            },
            navigateUp = { navigator.navigateUp() },
        )
    }
}
