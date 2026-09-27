package com.fserver.app.presentation.screens.source.details

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.filesSourceDetailsEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Files.SourceDetails> { key ->
        SourceDetailsScreen(
            key = key,
            navigateToActivity = { navigator.navigate(Destination.Activity) },
            navigateToFiles = { navigator.navigate(Destination.Files(sourceId = key.sourceId)) },
            navigateToDevice = { navigator.navigate(Destination.Settings.DeviceDetails(it)) },
            navigateToEdit = { navigator.navigate(Destination.Files.SourceEdit(key.sourceId)) },
            navigateUp = navigator::navigateUp,
        )
    }
}
