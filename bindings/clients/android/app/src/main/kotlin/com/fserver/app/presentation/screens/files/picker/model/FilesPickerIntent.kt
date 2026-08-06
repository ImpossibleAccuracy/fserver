package com.fserver.app.presentation.screens.files.picker.model

sealed interface FilesPickerIntent {
    /** Swiped away in the list. Drops the entry from the selection, nothing on disk. */
    data class EntryRemoved(val entry: FilesPickerState.PickedEntryUi) : FilesPickerIntent

    /** The add button. Opens the system picker once one is wired up. */
    data object AddClicked : FilesPickerIntent
}
