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
                // Back to whoever sent the user here: the picker is the one screen that pairs
                // mid-flow, and it turns the fresh session into the answer it owes its caller.
                val returnedToPicker = navigator.popTo { it is Destination.Connect }
                if (!returnedToPicker) {
                    // Not a sending flow, navigate to the file list
                    navigator.navigateByBackstack(listOf(Destination.Files.List))
                }
            },
            navigateUp = { navigator.navigateUp() },
        )
    }
}
