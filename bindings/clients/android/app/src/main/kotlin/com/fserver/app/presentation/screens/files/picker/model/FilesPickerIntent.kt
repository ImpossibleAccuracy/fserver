package com.fserver.app.presentation.screens.files.picker.model

sealed interface FilesPickerIntent {
    data class EntryRemoved(val entry: FilesPickerState.PickedEntryUi) : FilesPickerIntent

    data class UriPicked(val uri: android.net.Uri) : FilesPickerIntent

    data object FullAccessGranted : FilesPickerIntent

    data object MediaAccessGranted : FilesPickerIntent

    /** Leaves the active source's browser and returns to the source chooser. */
    data object SourceClosed : FilesPickerIntent

    /** Commits what every source contributed and moves on to picking a device. */
    data object DoneClicked : FilesPickerIntent

    data class DirectoryExpansionToggled(val id: String) : FilesPickerIntent

    data class DirectorySelectionToggled(val id: String) : FilesPickerIntent

    data class MediaGroupingSelected(
        val grouping: FilesPickerState.MediaGrouping,
    ) : FilesPickerIntent

    data class MediaTabSelected(val id: String) : FilesPickerIntent

    data class MediaSelectionToggled(val id: String) : FilesPickerIntent
}
