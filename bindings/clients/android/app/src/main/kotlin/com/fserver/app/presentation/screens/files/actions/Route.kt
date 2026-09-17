package com.fserver.app.presentation.screens.files.actions

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator
import com.fserver.app.presentation.navigation.scene.BottomSheetSceneStrategy

@OptIn(ExperimentalMaterial3Api::class)
fun EntryProviderScope<Destination>.filesActionsEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Files.Actions>(
        metadata = BottomSheetSceneStrategy.bottomSheet()
    ) {
        // The sheet is dismissed before the push: coming back from either branch should land on
        // the file list, not on the fork that is already answered.
        FilesActionsSheet(
            navigateToConnect = {
                navigator.navigateUp()
                navigator.navigate(Destination.Connect)
            },
            navigateToSourcePick = {
                navigator.navigateUp()
                navigator.navigate(Destination.Source.Setup.Pick())
            },
            dismiss = { navigator.navigateUp() },
        )
    }
}
