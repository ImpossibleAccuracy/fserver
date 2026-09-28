package com.fserver.app.presentation.screens.files.editor

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.filesImageEditorEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Files.ImageEditor> { key ->
        ImageEditorScreen(
            key = key,
            navigateUp = navigator::navigateUp,
        )
    }
}
