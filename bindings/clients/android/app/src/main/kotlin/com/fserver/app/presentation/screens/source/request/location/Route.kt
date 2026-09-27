package com.fserver.app.presentation.screens.source.request.location

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.syncRequestLocationEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Source.Request.Location> { key ->
        SyncRequestLocationScreen(
            key = key,
            navigateToPreferences = { location ->
                navigator.navigate(Destination.Source.Request.Preferences(key.sourceId, location))
            },
            navigateUp = { navigator.navigateUp() },
        )
    }
}
