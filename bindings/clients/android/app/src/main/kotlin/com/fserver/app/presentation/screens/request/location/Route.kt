package com.fserver.app.presentation.screens.request.location

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator
import com.fserver.app.presentation.screens.request.shared.isSyncRequestScreen

fun EntryProviderScope<Destination>.syncRequestLocationEntry(
    navigator: AppNavigator,
) {
    entry<Destination.SyncRequest.Location> { key ->
        SyncRequestLocationScreen(
            key = key,
            // The ask is answered by the time this lands: neither screen behind it has a question
            // left, and going back to them would offer to answer it twice.
            navigateToDone = {
                navigator.navigate(
                    screen = Destination.SyncRequest.Done(key.sourceId),
                    dropping = { it.isSyncRequestScreen },
                )
            },
            navigateUp = { navigator.navigateUp() },
        )
    }
}
