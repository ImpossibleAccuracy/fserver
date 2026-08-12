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
            // Connecting ends the onboarding flow: the pairing history behind it must not be
            // reachable by back once the user is inside the server.
            navigateToFiles = { navigator.navigateByBackstack(listOf(Destination.Files.List)) },
            navigateUp = { navigator.navigateUp() },
        )
    }
}
