package com.fserver.app.presentation.screens.source.shared.progress

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator
import com.fserver.app.presentation.screens.source.shared.closeSourceFlow

fun EntryProviderScope<Destination>.sourceProgressEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Source.Progress> { key ->
        SourceProgressScreen(
            key = key,
            navigateToDone = {
                navigator.navigate(
                    screen = Destination.Source.Done(key.sourceId),
                    dropping = { it is Destination.Source.Progress },
                )
            },
            closeFlow = { navigator.closeSourceFlow() },
        )
    }
}
