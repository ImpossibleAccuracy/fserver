package com.fserver.app.presentation.screens.source.setup.mode

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator
import com.fserver.app.presentation.screens.source.setup.shared.SourceSetupParent
import com.fserver.app.presentation.screens.source.shared.popToSourcePick
import com.fserver.app.presentation.screens.source.setup.shared.sourceSetupViewModel

fun EntryProviderScope<Destination>.sourceModeEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Source.Setup.Mode>(metadata = SourceSetupParent) {
        val flow = sourceSetupViewModel { navigator.popToSourcePick() } ?: return@entry

        SourceModeScreen(
            handler = flow.mode,
            navigateNext = {
                navigator.navigate(Destination.Source.Setup.Target)
            },
            navigateUp = { navigator.navigateUp() },
        )
    }
}
