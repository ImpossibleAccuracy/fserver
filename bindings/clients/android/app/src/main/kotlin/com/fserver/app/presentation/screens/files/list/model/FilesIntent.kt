package com.fserver.app.presentation.screens.files.list.model

import com.fserver.app.presentation.composable.model.FileUi
import com.fserver.app.presentation.composable.model.FilesViewModeUi

sealed interface FilesIntent {
    data class ViewModeSelected(val mode: FilesViewModeUi) : FilesIntent
    data class FileClicked(val file: FileUi) : FilesIntent
    data object SearchClicked : FilesIntent
}
