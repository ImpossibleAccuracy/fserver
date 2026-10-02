package com.fserver.app.presentation.screens.files

import com.fserver.app.util.stateInScreen
import com.fserver.app.presentation.shared.selection.Selection
import com.fserver.app.presentation.shared.sync.SyncTrigger
import com.fserver.app.presentation.composable.model.peers
import com.fserver.app.presentation.composable.model.peerOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.files.editor.shared.EditableImageFormat
import com.fserver.app.presentation.screens.files.model.FilesIntent
import com.fserver.app.presentation.screens.files.model.FilesState
import com.fserver.app.presentation.screens.files.model.FilesUiEffect
import com.fserver.app.presentation.screens.files.shared.FileFetches
import com.fserver.app.presentation.screens.files.shared.FilesProviderHandler
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.shared.browser.model.FileSortUi
import com.fserver.app.presentation.shared.browser.model.asPreviewFile
import com.fserver.app.presentation.shared.browser.model.toTree
import com.fserver.app.presentation.shared.error.ErrorReporter
import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.files.FilesController
import com.fserver.core.files.SyncFileEntry
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.storage.RegisteredSourcesRepository
import com.fserver.core.sync.SourcesController
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.model.drivesSync
import com.fserver.core.sync.model.pinsFiles
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Every synced file as one tree, narrowed by source and by where the bytes are. */
@OptIn(ExperimentalCoroutinesApi::class)
class FilesViewModel(
    key: Destination.Files,
    private val sourcesController: SourcesController,
    private val registeredSourcesRepository: RegisteredSourcesRepository,
    private val devicesRepository: DevicesRepository,
    private val filesController: FilesController,
    private val reporter: ErrorReporter,
) : ViewModel() {
    private val effects = Channel<FilesUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    private val filesProviderHandler = FilesProviderHandler(
        filesController = filesController,
        registeredSourcesRepository = registeredSourcesRepository,
        progress = sourcesController.progress,
        reporter = reporter,
        openFile = {
            effects.send(FilesUiEffect.OpenFile(it.asPreviewFile()))
        },
    )

    private val editable = MutableStateFlow(
        Editable(selectedSourceId = key.sourceId, filter = key.filter)
    )
    private val syncTrigger = SyncTrigger(viewModelScope, sourcesController, reporter)

    private val entries: StateFlow<FilesState.FeedUi?> = editable
        .map { Query(it.filter, it.selectedSourceId, it.sort, it.sortAscending) }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            combine(
                filesProviderHandler.loadEntries(
                    requiredLocation = when (query.filter) {
                        FilesState.FilterUi.All -> null
                        FilesState.FilterUi.Local -> FileBrowserUi.File.Location.Local
                        FilesState.FilterUi.Cloud -> FileBrowserUi.File.Location.Remote
                    },
                    sourceIds = query.sourceId?.let(::setOf),
                ),
                filesProviderHandler.fetches,
                registeredSourcesRepository.sources,
            ) { files, fetches, sources ->
                val writable = sources.filter { it.drivesSync }.mapTo(HashSet()) { it.id }
                val pinnable = sources.filter { it.pinsFiles }.mapTo(HashSet()) { it.id }

                FilesState.FeedUi(
                    preview = files.toTree(query.sort, query.sortAscending) { it.toUi(fetches) },
                    filter = query.filter,
                    sourceId = query.sourceId,
                    sort = query.sort,
                    sortAscending = query.sortAscending,
                    actions = files.associate { it.fileId to it.actions(writable, pinnable) },
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = null,
        )

    private val sources: Flow<List<FilesState.SourceUi>> = combine(
        devicesRepository.peers(),
        registeredSourcesRepository.sources,
    ) { peers, sources ->
        sources
            .map { FilesState.SourceUi(id = it.id, label = it.label, peer = peers.peerOf(it.deviceId)) }
            .sortedBy { it.label }
    }

    private val isSyncing: Flow<Boolean> = combine(
        syncTrigger.running,
        sourcesController.progress.passes,
    ) { isRefreshing, passes ->
        isRefreshing || passes.any { !it.isFinished }
    }

    val state: StateFlow<FilesState> = combine(
        editable,
        entries,
        sources,
        isSyncing,
    ) { edit, files, sources, syncing ->
        FilesState(
            sources = sources,
            selectedSourceId = edit.selectedSourceId.takeIf { id -> sources.any { it.id == id } },
            filter = edit.filter,
            entries = files,
            openedPath = edit.openedPath,
            sort = edit.sort,
            sortAscending = edit.sortAscending,
            isSyncing = syncing,
            editing = edit.selection.active,
            selected = edit.selection.ids,
        )
    }.stateInScreen(viewModelScope, FilesState())

    fun onIntent(intent: FilesIntent) {
        when (intent) {
            is FilesIntent.FiltersApplied -> editable.update {
                it.copy(selectedSourceId = intent.sourceId, filter = intent.filter)
            }

            FilesIntent.RefreshRequested -> syncTrigger.run("Sync from the files screen failed")

            is FilesIntent.EntryClicked -> openEntry(intent.entryId)

            is FilesIntent.FolderOpened -> editable.update { it.copy(openedPath = intent.path) }

            FilesIntent.FolderUp -> editable.update {
                val opened = state.value.openedDirectory
                it.copy(openedPath = opened?.let { dir -> state.value.entries?.preview?.parentOf(dir)?.path })
            }

            FilesIntent.FolderClosed -> editable.update { it.copy(openedPath = null) }

            is FilesIntent.SortSelected -> editable.update {
                it.copy(
                    sort = intent.sort,
                    sortAscending = if (it.sort == intent.sort) !it.sortAscending else true,
                )
            }

            is FilesIntent.EntryLongPressed -> editable.update {
                it.copy(selection = it.selection.started(intent.entryId))
            }

            is FilesIntent.EntryToggled -> editable.update {
                it.copy(selection = it.selection.toggled(intent.entryId, closeWhenEmpty = true))
            }

            FilesIntent.EditClosed -> closeEdit()

            is FilesIntent.RenameConfirmed -> rename(intent.entryId, intent.newName)

            is FilesIntent.DeleteConfirmed -> delete(intent.entryIds)

            is FilesIntent.PinRequested -> setPinned(intent.entryIds, intent.pinned)
        }
    }

    private fun rename(entryId: String, newName: String) {
        viewModelScope.launch {
            val entry = entryOf(entryId) ?: return@launch

            runCatchingCancellable { filesController.file(entry.sourceId, entry.fileId)?.rename(newName) }
                .onFailure { reporter.report(it, "Rename of ${entry.fileId} failed") }

            closeEdit()
        }
    }

    private fun delete(entryIds: Set<String>) {
        viewModelScope.launch {
            for (entryId in entryIds) {
                val entry = entryOf(entryId) ?: continue

                runCatchingCancellable { filesController.delete(entry.sourceId, entry.fileId) }
                    .onFailure { reporter.report(it, "Delete of ${entry.fileId} failed") }
            }

            closeEdit()
        }
    }

    private fun setPinned(entryIds: Set<String>, pinned: Boolean) {
        viewModelScope.launch {
            val bySource = entryIds.mapNotNull(::entryOf).groupBy({ it.sourceId }, { it.fileId })

            for ((sourceId, fileIds) in bySource) {
                runCatchingCancellable { filesController.setPinned(sourceId, fileIds.toSet(), pinned) }
                    .onFailure { reporter.report(it, "Pinning in $sourceId failed") }
            }

            closeEdit()
        }
    }

    private fun closeEdit() = editable.update { it.copy(selection = Selection()) }

    private fun entryOf(entryId: String): SyncFileEntry? =
        filesController.overallContent.value.find { it.fileId == entryId }

    private fun openEntry(entryId: String) {
        viewModelScope.launch {
            val file = entryOf(entryId) ?: return@launch

            filesProviderHandler.onItemClick(file)
        }
    }

    private data class Query(
        val filter: FilesState.FilterUi,
        val sourceId: String?,
        val sort: FileSortUi,
        val sortAscending: Boolean,
    )

    private data class Editable(
        val selectedSourceId: String? = null,
        val filter: FilesState.FilterUi = FilesState.FilterUi.All,
        /** The folder being browsed; the source and filter stay as they were when opened. */
        val openedPath: String? = null,
        val sort: FileSortUi = FileSortUi.Name,
        val sortAscending: Boolean = true,
        val selection: Selection = Selection(),
    )
}

private fun SyncFileEntry.toUi(fetches: FileFetches): FileBrowserUi.File = asPreviewFile().copy(
    sync = fetches.of(this),
)

private fun SyncFileEntry.actions(
    writable: Set<String>,
    pinnable: Set<String>,
): Set<FilesState.FileActionUi> {
    if (sourceId !in writable) return emptySet()

    return when (val state = localState) {
        is LocalIndexedFile.State.Present -> buildSet {
            add(FilesState.FileActionUi.Rename)
            add(FilesState.FileActionUi.Delete)
            if (EditableImageFormat.of(path) != null) add(FilesState.FileActionUi.Edit)
            if (sourceId in pinnable) {
                add(if (state.pinned) FilesState.FileActionUi.Unpin else FilesState.FileActionUi.Pin)
            }
        }
        is LocalIndexedFile.State.Evicted -> setOf(FilesState.FileActionUi.Delete)
        else -> emptySet()
    }
}
