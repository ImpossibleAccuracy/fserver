package com.fserver.app.presentation.screens.source.setup.progress

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator
import com.fserver.app.presentation.screens.source.setup.shared.SourceSetupParent
import com.fserver.app.presentation.screens.source.setup.shared.sourceSetupViewModel
import com.fserver.app.presentation.screens.source.shared.closeSourceFlow

fun EntryProviderScope<Destination>.sourceProgressEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Source.Setup.Progress>(metadata = SourceSetupParent) {
        // The source is registered by now, so a lost flow has nothing to restart — only to leave.
        val flow = sourceSetupViewModel { navigator.closeSourceFlow() } ?: return@entry

        SourceProgressScreen(
            handler = flow.progress,
            navigateToDone = {
                navigator.navigate(
                    screen = Destination.Source.Setup.Done,
                    dropping = { it is Destination.Source.Setup.Progress },
                )
            },
            closeFlow = { navigator.closeSourceFlow() },
        )
    }
}
