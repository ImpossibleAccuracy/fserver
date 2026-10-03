package com.fserver.app.presentation.screens.settings.storage.source.model

import com.fserver.app.presentation.shared.browser.model.FileKey

sealed interface StorageSourceIntent {
    data class SortChanged(val sort: StorageSourceState.SortUi) : StorageSourceIntent
    data class GroupingChanged(val grouped: Boolean) : StorageSourceIntent
    data object EditStarted : StorageSourceIntent
    data object EditClosed : StorageSourceIntent
    data class FileLongPressed(val file: FileKey) : StorageSourceIntent
    data class FileToggled(val file: FileKey) : StorageSourceIntent
    data object AllToggled : StorageSourceIntent
    data object FreeConfirmed : StorageSourceIntent
    data class FileClicked(val file: FileKey) : StorageSourceIntent
}
