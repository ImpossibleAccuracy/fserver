package com.fserver.app.presentation.screens.settings.storage.source.model

import com.fserver.app.presentation.shared.browser.model.FileBrowserUi

sealed interface StorageSourceUiEffect {
    data class OpenFile(val file: FileBrowserUi.File) : StorageSourceUiEffect
}
