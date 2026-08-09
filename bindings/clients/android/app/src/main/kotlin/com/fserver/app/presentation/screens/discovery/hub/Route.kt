package com.fserver.app.presentation.screens.discovery.hub

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.connectHubEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Connect> {
        ConnectHubScreen(
            navigateToNetworkSearch = { navigator.navigate(Destination.DeviceDiscovery) },
            navigateToQrScan = { navigator.navigate(Destination.QrScan) },
            navigateToManualAddress = { navigator.navigate(Destination.ManualAddress) },
        )
    }
}
