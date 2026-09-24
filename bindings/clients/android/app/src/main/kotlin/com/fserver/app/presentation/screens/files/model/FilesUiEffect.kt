package com.fserver.app.presentation.screens.files.model

import com.fserver.app.presentation.shared.browser.model.FileBrowserUi

sealed interface FilesUiEffect {
    data class OpenFile(val file: FileBrowserUi.File) : FilesUiEffect
}
