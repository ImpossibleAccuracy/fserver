package com.fserver.app.presentation.screens.pairing

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.pairingEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Pairing> { key ->
        PairingScreen(
            key = key,
            navigateNext = {
                // Navigate to next screen in current flow. Both flows that pair mid-way wait on
                // the target screen, so returning to it is the same move for either.
                val returnedToTarget = navigator.popTo { it is Destination.Source.Setup.Target }
                if (!returnedToTarget) {
                    // Not a sending flow, navigate to the file list
                    navigator.navigateByBackstack(listOf(Destination.Files.List))
                }
            },
            navigateUp = { navigator.navigateUp() },
        )
    }
}
