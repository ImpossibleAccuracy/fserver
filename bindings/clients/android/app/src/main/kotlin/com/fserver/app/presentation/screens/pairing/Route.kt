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
                // Navigate to next screen in current flow
                val returnedToSend = navigator.popTo { it is Destination.Files.SendTarget }
                if (!returnedToSend) {
                    // Not a sending flow, navigate to the file list
                    navigator.navigateByBackstack(listOf(Destination.Files.List))
                }
            },
            navigateUp = { navigator.navigateUp() },
        )
    }
}
