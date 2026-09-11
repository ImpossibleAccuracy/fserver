package com.fserver.app.presentation.screens.source.shared.done

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator
import com.fserver.app.presentation.screens.source.shared.closeSourceFlow
import com.fserver.app.presentation.screens.source.shared.popToSourcePick

fun EntryProviderScope<Destination>.sourceDoneEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Source.Done> { key ->
        SourceDoneScreen(
            key = key,
            navigateToFiles = { navigator.closeSourceFlow() },
            navigateToSourcePick = { navigator.popToSourcePick() },
        )
    }
}
