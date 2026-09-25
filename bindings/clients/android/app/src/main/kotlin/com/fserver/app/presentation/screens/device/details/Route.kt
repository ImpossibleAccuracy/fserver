package com.fserver.app.presentation.screens.device.details

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.settingsDeviceDetailsEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Settings.DeviceDetails> { key ->
        DeviceDetailsScreen(
            key = key,
            navigatePairing = { navigator.navigate(Destination.Pairing(it)) },
            navigateUp = navigator::navigateUp,
        )
    }
}
