package com.fserver.app.presentation.screens.discovery

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.deviceDiscoveryEntry(
    navigator: AppNavigator,
) {
    entry<Destination.DeviceDiscovery> {
        DeviceDiscoveryScreen(
            navigateToPairing = { deviceId -> navigator.navigate(Destination.Pairing(deviceId)) },
            navigateToQrScan = { navigator.navigate(Destination.QrScan) },
            // TODO: manual address entry has no screen yet.
            navigateToManualAddress = {},
        )
    }
}
