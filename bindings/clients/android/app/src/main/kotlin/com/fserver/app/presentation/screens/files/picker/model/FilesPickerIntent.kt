package com.fserver.app.presentation.screens.files.picker.model

sealed interface FilesPickerIntent {
    data class EntryRemoved(val entry: FilesPickerState.PickedEntryUi) : FilesPickerIntent

    data class UriPicked(val uri: android.net.Uri) : FilesPickerIntent

    data object FullAccessGranted : FilesPickerIntent

    data object MediaAccessGranted : FilesPickerIntent
}
