package com.fserver.app.presentation.screens.settings.devices

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.settingsDevicesEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Settings.Devices> {
        DevicesScreen(
            navigateToDevice = { navigator.navigate(Destination.Settings.DeviceDetails(it)) },
            navigateToConnect = { navigator.navigate(Destination.Connect) },
            navigateUp = navigator::navigateUp,
        )
    }
}
