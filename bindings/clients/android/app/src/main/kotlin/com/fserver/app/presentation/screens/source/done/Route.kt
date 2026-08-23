package com.fserver.app.presentation.screens.source.done

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.sourceDoneEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Source.Done> { key ->
        // The flow is answered: the screens behind this one would offer to configure a source
        // that already exists, so neither way out returns into them.
        val popToFiles = {
            if (!navigator.popTo { it is Destination.Files.List }) {
                navigator.navigateByBackstack(listOf(Destination.Files.List))
            }
        }

        SourceDoneScreen(
            key = key,
            navigateToFiles = { popToFiles() },
            navigateToSourcePick = {
                popToFiles()
                navigator.navigate(Destination.Source.Pick)
            },
        )
    }
}
