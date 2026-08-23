package com.fserver.app.presentation.screens.source.conditions

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.sourceConditionsEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Source.Conditions> { key ->
        SourceConditionsScreen(
            key = key,
            navigateToDone = {
                navigator.navigate(Destination.Source.Done(kind = key.kind, mode = key.mode))
            },
            navigateUp = { navigator.navigateUp() },
        )
    }
}
