package com.fserver.app.presentation.screens.source.request

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator
import com.fserver.app.presentation.screens.source.shared.isSourceScreen

fun EntryProviderScope<Destination>.syncRequestEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Source.Request.Details> { key ->
        SyncRequestScreen(
            key = key,
            // Back from the source lands where the request was opened from, not on the answered ask.
            navigateToSource = { sourceId ->
                navigator.navigate(
                    screen = Destination.Files.SourceDetails(sourceId),
                    dropping = { it.isSourceScreen },
                )
            },
            navigateUp = { navigator.navigateUp() },
        )
    }
}
