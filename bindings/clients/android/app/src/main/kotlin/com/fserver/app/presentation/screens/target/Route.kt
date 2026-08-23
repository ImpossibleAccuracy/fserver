package com.fserver.app.presentation.screens.target

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.model.TargetPurpose
import com.fserver.app.presentation.navigation.AppNavigator

/**
 * One entry for both flows: the purpose rides in the key, so what follows the chosen device is
 * decided here rather than by which destination happened to open the screen.
 */
fun EntryProviderScope<Destination>.targetDeviceEntry(
    navigator: AppNavigator,
) {
    entry<Destination.TargetDevice> { key ->
        TargetDeviceScreen(
            key = key,
            navigateToConnect = { navigator.navigate(Destination.Connect) },
            navigateToConditions = {
                val purpose = key.purpose
                if (purpose is TargetPurpose.ConfigureSource) {
                    navigator.navigate(
                        Destination.Source.Conditions(kind = purpose.kind, mode = purpose.mode)
                    )
                }
            },
            // Confirming ends the send flow; the discovery and pairing screens it went through
            // are not somewhere back should return to.
            navigateToFiles = {
                val popped = navigator.popTo { it is Destination.Files.List }
                if (!popped) {
                    navigator.navigateByBackstack(listOf(Destination.Files.List))
                }
            },
            navigateUp = { navigator.navigateUp() },
        )
    }
}
