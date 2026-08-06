package com.fserver.app.presentation.screens.files.picker.model

import com.fserver.app.presentation.model.PickedEntryUi

data class FilesPickerState(
    val entries: List<PickedEntryUi> = emptyList(),
) {
    val isEmpty: Boolean get() = entries.isEmpty()
}
