package com.fserver.app.presentation.screens.discovery.qr

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.qrScanEntry(
    navigator: AppNavigator,
) {
    entry<Destination.QrScan> {
        QrScanScreen(
            navigateToPairing = { target ->
                navigator.navigate(
                    Destination.Pairing(
                        deviceId = target.deviceId,
                        reconnectionArguments = target.reconnectionArguments
                    )
                )
            },
            navigateToManualAddress = { navigator.navigate(Destination.ManualAddress) },
            navigateUp = { navigator.navigateUp() },
        )
    }
}
