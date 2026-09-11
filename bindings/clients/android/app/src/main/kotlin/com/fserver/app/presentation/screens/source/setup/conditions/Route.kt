package com.fserver.app.presentation.screens.source.setup.conditions

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator
import com.fserver.app.presentation.screens.source.setup.shared.SourceSetupParent
import com.fserver.app.presentation.screens.source.setup.shared.isAnsweredSourceSetupScreen
import com.fserver.app.presentation.screens.source.setup.shared.sourceSetupViewModel
import com.fserver.app.presentation.screens.source.shared.popToSourcePick

fun EntryProviderScope<Destination>.sourceConditionsEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Source.Setup.Conditions>(metadata = SourceSetupParent) { _ ->
        val flow = sourceSetupViewModel { navigator.popToSourcePick() } ?: return@entry

        SourceConditionsScreen(
            handler = flow.conditions,
            // Everything answered goes with it: the source is registered from here on, and none
            // of those screens has a question left to ask.
            navigateToProgress = { sourceId ->
                navigator.navigate(
                    screen = Destination.Source.Progress(sourceId),
                    dropping = { it.isAnsweredSourceSetupScreen },
                )
            },
            navigateUp = { navigator.navigateUp() },
        )
    }
}
