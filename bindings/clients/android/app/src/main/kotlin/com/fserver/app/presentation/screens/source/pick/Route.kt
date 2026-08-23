package com.fserver.app.presentation.screens.source.pick

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.sourcePickEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Source.Pick> {
        SourcePickScreen(
            navigateToAccess = { kind -> navigator.navigate(Destination.Source.Access(kind)) },
            navigateUp = { navigator.navigateUp() },
        )
    }
}
