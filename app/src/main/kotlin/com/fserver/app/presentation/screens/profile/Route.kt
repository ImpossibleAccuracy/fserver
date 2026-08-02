package com.fserver.app.presentation.screens.profile

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.serverProfileEntry(
    navigator: AppNavigator,
) {
    entry<Destination.ServerProfile> {
        ServerProfileScreen(
            // Applying the profile ends the connection flow; the scan behind it must not be
            // reachable by back once the user is inside the server.
            navigateToFiles = { navigator.navigateByBackstack(listOf(Destination.Files)) },
            // TODO: manual address entry has no screen yet.
            navigateToManualEditor = {},
            navigateUp = { navigator.navigateUp() },
        )
    }
}
