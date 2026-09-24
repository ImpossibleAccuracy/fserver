package com.fserver.app.presentation.screens.settings.mydevice

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.settingsMyDeviceEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Settings.MyDevice> {
        MyDeviceScreen(navigateUp = navigator::navigateUp)
    }
}
