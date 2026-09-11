package com.fserver.app.presentation.screens.source.request.location

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator
import com.fserver.app.presentation.screens.source.shared.isSourceScreen

fun EntryProviderScope<Destination>.syncRequestLocationEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Source.Request.Location> { key ->
        SyncRequestLocationScreen(
            key = key,
            // The ask is answered by the time this lands: neither screen behind it has a question
            // left, and going back to them would offer to answer it twice.
            navigateToProgress = {
                navigator.navigate(
                    screen = Destination.Source.Progress(key.sourceId),
                    dropping = { it.isSourceScreen },
                )
            },
            navigateUp = { navigator.navigateUp() },
        )
    }
}
