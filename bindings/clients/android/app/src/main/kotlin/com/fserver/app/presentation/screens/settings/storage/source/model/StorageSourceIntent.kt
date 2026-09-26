package com.fserver.app.presentation.screens.settings.storage.source.model

sealed interface StorageSourceIntent {
    data class SortChanged(val sort: StorageSourceState.SortUi) : StorageSourceIntent
    data class GroupingChanged(val grouped: Boolean) : StorageSourceIntent
    data object EditStarted : StorageSourceIntent
    data object EditClosed : StorageSourceIntent
    data class FileLongPressed(val id: String) : StorageSourceIntent
    data class FileToggled(val id: String) : StorageSourceIntent
    data object AllToggled : StorageSourceIntent
    data object DeleteConfirmed : StorageSourceIntent
}
