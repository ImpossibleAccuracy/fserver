package com.fserver.app.presentation.screens.files.source

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator
import com.fserver.app.presentation.navigation.scene.BottomSheetSceneStrategy
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
fun EntryProviderScope<Destination>.filesSourceActionsEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Files.SourceActions>(
        metadata = BottomSheetSceneStrategy.bottomSheet()
    ) { key ->
        SourceActionsSheet(
            viewModel = koinViewModel { parametersOf(key) },
            dismiss = { navigator.navigateUp() },
        )
    }
}
