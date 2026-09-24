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
            navigateToSourcePick = { deviceId ->
                navigator.navigate(Destination.Source.Setup.Pick(targetDeviceId = deviceId))
            },
            navigateToSyncRequests = { navigator.navigate(Destination.Source.Request.List) },
            navigateToSourceDetails = {
                navigator.navigate(Destination.Files.SourceDetails(sourceId = it))
            },
            navigateToDeviceSettings = {
                navigator.navigate(Destination.Settings.DeviceDetails(it))
            },
            navigateToManualAddress = { navigator.navigate(Destination.ManualAddress) },
            navigateToQrScan = { navigator.navigate(Destination.QrScan) },
        )
    }
}
