package com.fserver.app.presentation.screens.settings.transfers

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.settingsTransfersEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Settings.Transfers> {
        TransfersScreen(navigateUp = navigator::navigateUp)
    }
}
