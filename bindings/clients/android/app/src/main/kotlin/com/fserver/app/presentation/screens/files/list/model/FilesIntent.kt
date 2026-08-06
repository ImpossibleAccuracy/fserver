package com.fserver.app.presentation.screens.files.list.model

import com.fserver.app.presentation.model.FileUi
import com.fserver.app.presentation.model.FilesViewModeUi

sealed interface FilesIntent {
    data class ViewModeSelected(val mode: FilesViewModeUi) : FilesIntent
    data class FileClicked(val file: FileUi) : FilesIntent
    data object SearchClicked : FilesIntent
    data object IncomingDemoRequested : FilesIntent
    data object IncomingRequestDismissed : FilesIntent
}
