package com.fserver.app.presentation.screens.dashboard

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.dashboardEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Dashboard> {
        DashboardScreen(
            navigateToFiles = { navigator.navigate(Destination.Files) },
            navigateToConnect = { navigator.navigate(Destination.Connect) },
            navigateToSourcePick = { navigator.navigate(Destination.Source.Setup.Pick()) },
            navigateToSyncRequests = { navigator.navigate(Destination.Source.Request.List) },
            navigateToSourceDetails = {
                navigator.navigate(Destination.Files.SourceDetails(sourceId = it))
            },
            navigateToDeviceSettings = {
                navigator.navigate(Destination.Settings.DeviceDetails(it))
            },
            navigateToStorage = { navigator.navigate(Destination.Settings.Storage) },
        )
    }
}
