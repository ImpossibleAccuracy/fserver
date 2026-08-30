package com.fserver.app.presentation.screens.source.done

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.sourceDoneEntry(
    navigator: AppNavigator,
) {
    // Outside the flow's ViewModel scope on purpose: getting here drops every screen the store
    // was tied to, so this one reads its summary out of the key.
    entry<Destination.Source.Done> { key ->
        SourceDoneScreen(
            summary = key.summary,
            navigateToFiles = { navigator.navigateUp() },
            navigateToSourcePick = {
                navigator.navigateUp()
                navigator.navigate(Destination.Source.Pick)
            },
        )
    }
}
