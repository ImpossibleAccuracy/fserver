package com.fserver.app.presentation.screens.files.folder

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

fun EntryProviderScope<Destination>.filesFolderEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Files.Folder> { key ->
        FolderScreen(
            viewModel = koinViewModel { parametersOf(key) },
            navigateToFolder = { folder ->
                navigator.navigate(
                    Destination.Files.Folder(folderPath = folder)
                )
            },
            navigateUp = { navigator.navigateUp() },
        )
    }
}
