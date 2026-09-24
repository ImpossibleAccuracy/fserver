package com.fserver.app.presentation.screens.source.details

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.filesSourceDetailsEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Files.SourceDetails> { key ->
        SourceDetailsScreen(
            sourceId = key.sourceId,
            navigateUp = { navigator.navigateUp() },
        )
    }
}
