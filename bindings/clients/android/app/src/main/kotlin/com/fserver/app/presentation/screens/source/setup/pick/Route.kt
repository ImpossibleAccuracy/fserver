package com.fserver.app.presentation.screens.source.setup.pick

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator
import com.fserver.app.presentation.screens.source.setup.shared.ownedSourceSetupViewModel

fun EntryProviderScope<Destination>.sourcePickEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Source.Setup.Pick> {
        val flow = ownedSourceSetupViewModel()

        SourcePickScreen(
            handler = flow.pick,
            navigateToAccess = { kind ->
                flow.start(kind)
                navigator.navigate(Destination.Source.Setup.Access(kind))
            },
            navigateUp = { navigator.navigateUp() },
        )
    }
}
