package com.fserver.app.presentation.screens.files.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.files.folder.model.FolderIntent
import com.fserver.app.presentation.screens.files.folder.model.FolderState
import com.fserver.app.presentation.screens.files.folder.model.FolderUiEffect
import com.fserver.app.presentation.screens.files.shared.FilesProviderHandler
import com.fserver.app.presentation.screens.source.shared.preview.model.SourcePreviewUi
import com.fserver.app.presentation.screens.source.shared.preview.model.asPreviewFile
import com.fserver.app.presentation.screens.source.shared.preview.model.directoryName
import com.fserver.app.presentation.screens.source.shared.preview.model.isMediaCollection
import com.fserver.core.files.FilesController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FolderViewModel(
    private val key: Destination.Files.Folder,
    private val filesController: FilesController,
) : ViewModel() {
    private val effects = Channel<FolderUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    private val filesProviderHandler = FilesProviderHandler(
        filesController = filesController,
        openFile = {
            viewModelScope.launch {
                effects.send(FolderUiEffect.OpenFile(it.asPreviewFile()))
            }
        }
    )

    private val editable = MutableStateFlow(Editable())

    private val entries = filesProviderHandler.loadPreviewFiles(key.folderPath)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = null,
        )

    val state: StateFlow<FolderState> = combine(
        editable,
        entries
    ) { edit, files ->
        FolderState(
            title = directoryName(key.folderPath),
            summary = null, // TODO
            entries = files?.let {
                val isMedia = edit.forceMediaPreviewType ?: isMediaCollection(it)

                if (isMedia) SourcePreviewUi.Gallery(files)
                else SourcePreviewUi.PlainList(files)
            },
            showsCloudNotice = files != null &&
                    files.any { it.location == SourcePreviewUi.File.Location.Remote },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FolderState(),
    )

    fun onIntent(intent: FolderIntent) {
        when (intent) {
            FolderIntent.ViewToggled -> editable.update {
                it.copy(
                    forceMediaPreviewType = it.forceMediaPreviewType?.not()
                        ?: state.value.isMediaCollection.not()
                )
            }

            is FolderIntent.ItemClicked -> {
                viewModelScope.launch {
                    val file = filesController.overallContent.value
                        .find { it.fileId == intent.itemId }
                        ?: return@launch

                    filesProviderHandler.onItemClick(file)
                }
            }
        }
    }

    private data class Editable(val forceMediaPreviewType: Boolean? = null)
}
