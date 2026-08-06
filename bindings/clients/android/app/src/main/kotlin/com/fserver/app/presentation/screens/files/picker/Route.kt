package com.fserver.app.presentation.screens.files.picker

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator
import com.fserver.app.presentation.navigation.scene.BottomSheetSceneStrategy

@OptIn(ExperimentalMaterial3Api::class)
fun EntryProviderScope<Destination>.filesPickerEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Files.Picker>(
        metadata = BottomSheetSceneStrategy.bottomSheet(
            options = BottomSheetSceneStrategy.BottomSheetOptions(
                showDragHandle = false,
            )
        )
    ) {
        FilesPickerScreen(
            navigateUp = { navigator.navigateUp() },
        )
    }
}
