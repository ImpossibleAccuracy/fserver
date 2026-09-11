package com.fserver.app.presentation.screens.source.setup.target

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator
import com.fserver.app.presentation.screens.source.setup.shared.SourceSetupParent
import com.fserver.app.presentation.screens.source.setup.shared.sourceSetupViewModel
import com.fserver.app.presentation.screens.source.shared.popToSourcePick

fun EntryProviderScope<Destination>.sourceTargetEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Source.Setup.Target>(metadata = SourceSetupParent) {
        val flow = sourceSetupViewModel { navigator.popToSourcePick() } ?: return@entry

        SourceTargetScreen(
            handler = flow.target,
            navigateToPairing = { navigator.navigate(Destination.Pairing(it)) },
            navigateNext = {
                val state = flow.state.value
                val kind = state.kind
                val mode = state.mode

                if (kind != null && mode != null) {
                    navigator.navigate(
                        screen = Destination.Source.Setup.Conditions(kind = kind, mode = mode),
                        dropping = {
                            it is Destination.Source.Setup.Mode ||
                                it is Destination.Source.Setup.Target
                        },
                    )
                }
            },
            navigateUp = { navigator.navigateUp() },
        )
    }
}
