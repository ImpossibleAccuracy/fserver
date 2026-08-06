package com.fserver.app.presentation.screens.files.picker

import androidx.lifecycle.ViewModel
import com.fserver.app.data.DemoContentSource
import com.fserver.app.presentation.screens.files.picker.model.FilesPickerIntent
import com.fserver.app.presentation.screens.files.picker.model.FilesPickerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The selection the user is assembling before sending it to the server: files and whole
 * directories side by side.
 *
 * Removing an entry here only shrinks the selection — it never touches the file itself,
 * which is the same `evict` ≠ `delete` line the offload mode draws.
 */
class FilesPickerViewModel(
    private val content: DemoContentSource,
) : ViewModel() {

    private val _state = MutableStateFlow(
        FilesPickerState(entries = content.pickedEntries())
    )
    val state: StateFlow<FilesPickerState> = _state.asStateFlow()

    fun onIntent(intent: FilesPickerIntent) {
        when (intent) {
            is FilesPickerIntent.EntryRemoved ->
                _state.update { it.copy(entries = it.entries - intent.entry) }

            // The system picker is not wired up yet, so nothing is added for now.
            FilesPickerIntent.AddClicked -> Unit
        }
    }
}
