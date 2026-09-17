package com.fserver.app.presentation.screens.files.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.files.folder.model.FolderIntent
import com.fserver.app.presentation.screens.files.folder.model.FolderState
import com.fserver.app.presentation.screens.files.folder.model.FolderUiEffect
import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.app.presentation.screens.files.shared.FilesProviderHandler
import com.fserver.app.presentation.screens.source.shared.preview.model.SourcePreviewUi
import com.fserver.app.presentation.screens.source.shared.preview.model.asPreviewFile
import com.fserver.app.presentation.screens.source.shared.preview.model.directoryName
import com.fserver.app.presentation.screens.source.shared.preview.model.isMediaCollection
import com.fserver.core.files.FilesController
import com.fserver.core.storage.RegisteredSourcesRepository
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
    private val registeredSourcesRepository: RegisteredSourcesRepository,
    private val filesController: FilesController,
) : ViewModel() {
    private val effects = Channel<FolderUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    private val filesProviderHandler = FilesProviderHandler(
        filesController = filesController,
        registeredSourcesRepository = registeredSourcesRepository,
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
                val sorted = it.sorted(edit.sort, edit.sortAscending)
                val isMedia = edit.forceMediaPreviewType ?: isMediaCollection(sorted)

                if (isMedia) SourcePreviewUi.Gallery(sorted)
                else SourcePreviewUi.PlainList(sorted)
            },
            showsCloudNotice = files != null &&
                    files.any { it.location == SourcePreviewUi.File.Location.Remote },
            sort = edit.sort,
            sortAscending = edit.sortAscending,
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

            is FolderIntent.SortSelected -> editable.update {
                it.copy(
                    sort = intent.sort,
                    sortAscending = if (it.sort == intent.sort) !it.sortAscending else true,
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

    private data class Editable(
        val forceMediaPreviewType: Boolean? = null,
        val sort: FolderState.SortUi = FolderState.SortUi.Name,
        val sortAscending: Boolean = true,
    )
}

private fun List<SourcePreviewUi.File>.sorted(
    sort: FolderState.SortUi,
    ascending: Boolean,
): List<SourcePreviewUi.File> {
    val comparator = when (sort) {
        FolderState.SortUi.Name -> compareBy<SourcePreviewUi.File> { it.name.lowercase() }
        FolderState.SortUi.Date -> compareBy { it.modifiedAt }
        FolderState.SortUi.Size -> compareBy { it.size?.bytes }
        FolderState.SortUi.Kind -> compareBy<SourcePreviewUi.File> { it.kind }
            .thenBy { it.extensionLabel.orEmpty() }
            .thenBy { it.name.lowercase() }
    }

    return sortedWith(
        compareByDescending<SourcePreviewUi.File> { it.kind == FileKindUi.Folder }
            .then(if (ascending) comparator else comparator.reversed())
    )
}
