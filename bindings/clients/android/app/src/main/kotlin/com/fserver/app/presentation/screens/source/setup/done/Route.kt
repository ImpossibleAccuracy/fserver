package com.fserver.app.presentation.screens.source.setup.done

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator
import com.fserver.app.presentation.screens.source.setup.shared.SourceSetupParent
import com.fserver.app.presentation.screens.source.setup.shared.sourceSetupViewModel
import com.fserver.app.presentation.screens.source.shared.closeSourceFlow
import com.fserver.app.presentation.screens.source.shared.popToSourcePick

fun EntryProviderScope<Destination>.sourceDoneEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Source.Setup.Done>(metadata = SourceSetupParent) {
        val flow = sourceSetupViewModel { navigator.closeSourceFlow() } ?: return@entry

        SourceDoneScreen(
            handler = flow.done,
            navigateToFiles = { navigator.closeSourceFlow() },
            navigateToSourcePick = { navigator.popToSourcePick() },
        )
    }
}
