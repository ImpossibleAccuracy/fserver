package com.fserver.app.presentation.screens.files.picker.model

sealed interface FilesPickerUiEffect {
    /** The selection is stored; [selectionId] is what the next screen looks it up by. */
    data class SelectionReady(val selectionId: String) : FilesPickerUiEffect
}
