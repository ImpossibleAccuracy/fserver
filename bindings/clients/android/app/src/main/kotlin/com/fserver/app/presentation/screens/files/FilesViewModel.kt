package com.fserver.app.presentation.screens.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.files.model.FilesIntent
import com.fserver.app.presentation.screens.files.model.FilesState
import com.fserver.app.presentation.screens.files.model.FilesUiEffect
import com.fserver.app.presentation.screens.files.shared.FilesProviderHandler
import com.fserver.app.presentation.screens.source.shared.model.latest
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
import com.fserver.core.storage.TrustedDevicesRepository
import com.fserver.core.sync.SourcesController
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.model.drivesSync
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
    private val trustedDevicesRepository: TrustedDevicesRepository,
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
        reporter = reporter,
        openFile = {
            effects.send(FilesUiEffect.OpenFile(it.asPreviewFile()))
        },
    )

    private val editable = MutableStateFlow(
        Editable(selectedSourceId = key.sourceId, filter = key.filter)
    )
    private val refreshing = MutableStateFlow(false)

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
                filesProviderHandler.downloading,
                registeredSourcesRepository.sources,
            ) { files, downloading, sources ->
                val writable = sources.filter { it.drivesSync }.mapTo(HashSet()) { it.id }

                FilesState.FeedUi(
                    preview = files.toTree(query.sort, query.sortAscending) { it.toUi(downloading) },
                    filter = query.filter,
                    sourceId = query.sourceId,
                    sort = query.sort,
                    sortAscending = query.sortAscending,
                    actions = files.associate { it.fileId to it.actions(writable) },
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = null,
        )

    private val sources: Flow<List<FilesState.SourceUi>> = combine(
        trustedDevicesRepository.devices,
        devicesRepository.devices.connected,
        registeredSourcesRepository.sources,
    ) { trusted, connected, sources ->
        val online = connected.associateBy { it.deviceId }

        sources
            .map { source ->
                val record = trusted.latest(source.deviceId)
                val session = online[source.deviceId]

                FilesState.SourceUi(
                    id = source.id,
                    label = source.label,
                    deviceName = session?.displayName ?: record?.displayName ?: source.deviceId,
                    deviceKind = session?.kind ?: record?.metadata?.kind,
                )
            }
            .sortedBy { it.label }
    }

    private val isSyncing: Flow<Boolean> = combine(
        refreshing,
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
            editing = edit.editing,
            selected = edit.selected,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FilesState(),
    )

    fun onIntent(intent: FilesIntent) {
        when (intent) {
            is FilesIntent.FiltersApplied -> editable.update {
                it.copy(selectedSourceId = intent.sourceId, filter = intent.filter)
            }

            FilesIntent.RefreshRequested -> runSync()

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
                it.copy(editing = true, selected = it.selected + intent.entryId)
            }

            is FilesIntent.EntryToggled -> editable.update { it.toggled(intent.entryId) }

            FilesIntent.EditClosed -> closeEdit()

            is FilesIntent.EditRequested -> Unit

            is FilesIntent.RenameConfirmed -> rename(intent.entryId, intent.newName)

            is FilesIntent.DeleteConfirmed -> delete(intent.entryIds)
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

    private fun closeEdit() = editable.update { it.copy(editing = false, selected = emptySet()) }

    private fun entryOf(entryId: String): SyncFileEntry? =
        filesController.overallContent.value.find { it.fileId == entryId }

    private fun openEntry(entryId: String) {
        viewModelScope.launch {
            val file = entryOf(entryId) ?: return@launch

            filesProviderHandler.onItemClick(file)
        }
    }

    private fun runSync() {
        if (refreshing.value) return
        refreshing.value = true

        viewModelScope.launch {
            runCatching { sourcesController.runSync() }
                .exceptionOrNull()
                ?.let { reporter.report(it, "Sync from the files screen failed") }

            refreshing.value = false
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
        val editing: Boolean = false,
        val selected: Set<String> = emptySet(),
    ) {
        fun toggled(entryId: String): Editable {
            val next = if (entryId in selected) selected - entryId else selected + entryId
            return copy(selected = next, editing = next.isNotEmpty())
        }
    }
}

private fun SyncFileEntry.toUi(downloading: Set<String>): FileBrowserUi.File = asPreviewFile().copy(
    sync = FileBrowserUi.File.Sync.Receiving.takeIf { fileId in downloading },
)

private fun SyncFileEntry.actions(writable: Set<String>): Set<FilesState.FileActionUi> = when {
    sourceId !in writable -> emptySet()
    localState is LocalIndexedFile.State.Present -> FilesState.FileActionUi.entries.toSet()
    localState is LocalIndexedFile.State.Evicted -> setOf(FilesState.FileActionUi.Delete)
    else -> emptySet()
}
