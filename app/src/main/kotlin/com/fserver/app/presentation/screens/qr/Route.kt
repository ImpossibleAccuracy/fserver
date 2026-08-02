package com.fserver.app.presentation.screens.qr

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.qrScanEntry(
    navigator: AppNavigator,
) {
    entry<Destination.QrScan> {
        QrScanScreen(
            navigateToProfile = { navigator.navigate(Destination.ServerProfile) },
            // TODO: manual address entry has no screen yet.
            navigateToManualAddress = {},
            navigateUp = { navigator.navigateUp() },
        )
    }
}
