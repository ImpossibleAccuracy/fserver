package com.fserver.app.presentation.screens.files.list.model

import com.fserver.app.presentation.composable.shared.FileUi
import com.fserver.app.presentation.composable.shared.FilesViewModeUi

sealed interface FilesIntent {
    data class ViewModeSelected(val mode: FilesViewModeUi) : FilesIntent
    data class FileClicked(val file: FileUi) : FilesIntent
    data object SearchClicked : FilesIntent
}
