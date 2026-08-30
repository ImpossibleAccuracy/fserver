package com.fserver.app.presentation.screens.source.pick

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator
import com.fserver.app.presentation.screens.source.shared.ownedSourceFlowViewModel

fun EntryProviderScope<Destination>.sourcePickEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Source.Pick> {
        val flow = ownedSourceFlowViewModel()

        SourcePickScreen(
            viewModel = flow,
            navigateToAccess = { kind ->
                flow.start(kind)
                navigator.navigate(Destination.Source.Access(kind))
            },
            navigateUp = { navigator.navigateUp() },
        )
    }
}
