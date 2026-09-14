package com.fserver.app.presentation.screens.discovery.connect

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator
import com.fserver.app.presentation.navigation.LocalResultEventBus
import com.fserver.app.presentation.screens.discovery.connect.model.DeviceSelection

fun EntryProviderScope<Destination>.connectEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Connect> {
        val results = LocalResultEventBus.current

        ConnectScreen(
            // The pick is published and the screen leaves. Whoever opened it reads the result
            // with `ResultEffect`; a caller that only wanted a connection ignores it and is
            // already where it wants to be.
            onDeviceSelected = { deviceId ->
                results.sendResult(DeviceSelection(deviceId))
                navigator.navigateUp()
            },
            navigateToPairing = { navigator.navigate(Destination.Pairing(it)) },
            navigateToQrScan = { navigator.navigate(Destination.QrScan) },
            navigateToManualAddress = { navigator.navigate(Destination.ManualAddress) },
            navigateUp = { navigator.navigateUp() },
        )
    }
}
