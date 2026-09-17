package com.fserver.app.presentation.screens.files.list

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.filesListEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Files.List> {
        FilesScreen(
            navigateToActions = { navigator.navigate(Destination.Files.Actions) },
            navigateToConnect = { navigator.navigate(Destination.Connect) },
            navigateToSourcePick = { deviceId ->
                navigator.navigate(Destination.Source.Setup.Pick(targetDeviceId = deviceId))
            },
            navigateToSyncRequests = { navigator.navigate(Destination.Source.Request.List) },
            navigateToFolder = { folder ->
                navigator.navigate(
                    Destination.Files.Folder(folderPath = folder)
                )
            },
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
