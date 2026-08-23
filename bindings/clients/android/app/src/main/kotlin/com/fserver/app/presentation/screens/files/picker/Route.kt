package com.fserver.app.presentation.screens.files.picker

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.model.TargetPurpose
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
            // The sheet is dismissed before the push, so back from the target list lands on the
            // file list rather than on a picker holding a selection already committed.
            navigateToTarget = { selectionId ->
                navigator.navigateUp()
                navigator.navigate(
                    Destination.TargetDevice(TargetPurpose.SendFiles(selectionId))
                )
            },
            navigateUp = { navigator.navigateUp() },
        )
    }
}
