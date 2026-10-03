package com.fserver.app.presentation.screens.settings.storage.main.model

sealed interface StorageUiEffect {
    data class ExportFinished(val files: Int, val skipped: Int) : StorageUiEffect

    data object ExportFailed : StorageUiEffect
}
