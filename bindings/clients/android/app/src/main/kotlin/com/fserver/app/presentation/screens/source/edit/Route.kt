package com.fserver.app.presentation.screens.source.edit

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.filesSourceEditEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Files.SourceEdit> { key ->
        SourceEditScreen(
            key = key,
            navigateUp = navigator::navigateUp,
        )
    }
}
