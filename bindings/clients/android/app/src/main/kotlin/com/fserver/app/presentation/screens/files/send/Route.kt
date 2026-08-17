package com.fserver.app.presentation.screens.files.send

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.sendTargetEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Files.SendTarget> { key ->
        SendTargetScreen(
            key = key,
            navigateToNetworkSearch = { navigator.navigate(Destination.DeviceDiscovery) },
            navigateToQrScan = { navigator.navigate(Destination.QrScan) },
            navigateToManualAddress = { navigator.navigate(Destination.ManualAddress) },
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
