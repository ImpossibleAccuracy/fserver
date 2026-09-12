package com.fserver.app.presentation.screens.files.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.files.folder.model.FolderIntent
import com.fserver.app.presentation.screens.files.folder.model.FolderState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class FolderViewModel(
    private val key: Destination.Files.Folder,
) : ViewModel() {

    private data class Editable(val mediaCollection: Boolean)

    private val editable = MutableStateFlow(Editable(mediaCollection = key.mediaCollection))

    val state: StateFlow<FolderState> = editable
        .map { it.toPresentation() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = editable.value.toPresentation(),
        )

    fun onIntent(intent: FolderIntent) {
        when (intent) {
            FolderIntent.ViewToggled -> editable.update {
                it.copy(mediaCollection = !it.mediaCollection)
            }

            is FolderIntent.ItemClicked -> Unit
        }
    }

    private fun Editable.toPresentation() = FolderState(
        title = key.title,
        summary = SampleSummary,
        mediaCollection = mediaCollection,
        items = FolderState.SampleItems,
        showsCloudNotice = true,
    )

    private companion object {
        const val SampleSummary = "Offload · older than 30 days · 1 240"
    }
}
