package com.fserver.app.presentation.screens.settings.storage.source

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.data.AppSettingsStore
import com.fserver.app.presentation.composable.model.direction
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.files.shared.FilesProviderHandler
import com.fserver.app.presentation.screens.settings.storage.main.model.peerOf
import com.fserver.app.presentation.screens.settings.storage.main.model.peers
import com.fserver.app.presentation.screens.settings.storage.source.model.StorageSourceIntent
import com.fserver.app.presentation.screens.settings.storage.source.model.StorageSourceState
import com.fserver.app.presentation.screens.settings.storage.source.model.StorageSourceState.SortUi
import com.fserver.app.presentation.screens.settings.storage.source.model.StorageSourceUiEffect
import com.fserver.app.presentation.screens.source.shared.model.readablePath
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.shared.browser.model.FileSortUi
import com.fserver.app.presentation.shared.browser.model.asPreviewFile
import com.fserver.app.presentation.shared.browser.model.toTree
import com.fserver.app.presentation.shared.error.ErrorReporter
import com.fserver.app.util.combineMany
import com.fserver.core.files.FilesController
import com.fserver.core.files.SyncFileEntry
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.storage.RegisteredSourcesRepository
import com.fserver.core.storage.TrustedDevicesRepository
import com.fserver.core.sync.SourcesController
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.model.SourceEntry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StorageSourceViewModel(
    private val key: Destination.Settings.StorageSource,
    registeredSources: RegisteredSourcesRepository,
    filesController: FilesController,
    sourcesController: SourcesController,
    trustedDevices: TrustedDevicesRepository,
    devicesRepository: DevicesRepository,
    private val appSettings: AppSettingsStore,
    reporter: ErrorReporter,
) : ViewModel() {
    private val effects = Channel<StorageSourceUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    private val filesProviderHandler = FilesProviderHandler(
        filesController = filesController,
        registeredSourcesRepository = registeredSources,
        reporter = reporter,
        openFile = {
            effects.send(StorageSourceUiEffect.OpenFile(it.asPreviewFile()))
        },
    )

    private val entries: StateFlow<List<SyncFileEntry>> = filesProviderHandler
        .loadEntries(sourceIds = setOf(key.sourceId), rooted = false)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val editable = MutableStateFlow(Editable())

    val state: StateFlow<StorageSourceState?> = combineMany(
        registeredSources.observeById(key.sourceId),
        entries,
        sourcesController.progress.transfers,
        peers(trustedDevices, devicesRepository),
        appSettings.storageGroupedByFolder(key.sourceId),
        editable,
    ) { source, entries, transfers, peers, grouped, editable ->
        if (source == null) return@combineMany null

        val sending = transfers
            .filter { !it.isFinished && it.key.sourceId == source.id }
            .mapTo(mutableSetOf()) { it.key.fileId }
        val here = entries.filter { it.localState is LocalIndexedFile.State.Present }
        val fileOf = { entry: SyncFileEntry -> entry.toUi(sending) }
        val files = here.map(fileOf).sortedWith(editable.sort.comparator)
        val ids = files.mapTo(mutableSetOf()) { it.id }
        val hasFolders = here.any { '/' in it.path }

        StorageSourceState(
            isLoading = false,
            label = source.label,
            path = source.path(),
            peer = peers.peerOf(source.deviceId),
            direction = source.direction(),
            offPhoneFiles = entries.size - here.size,
            sort = editable.sort,
            grouped = grouped && hasFolders,
            hasFolders = hasFolders,
            files = files,
            tree = if (grouped && hasFolders) {
                here.toTree(editable.sort.fileSort, editable.sort.ascending, fileOf)
            } else {
                FileBrowserUi.Tree()
            },
            editing = editable.editing && files.isNotEmpty(),
            selected = editable.selected intersect ids,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    fun onIntent(intent: StorageSourceIntent) {
        when (intent) {
            is StorageSourceIntent.SortChanged -> editable.update { it.copy(sort = intent.sort) }
            is StorageSourceIntent.GroupingChanged -> setGrouped(intent.grouped)
            StorageSourceIntent.EditStarted -> editable.update { it.copy(editing = true) }
            StorageSourceIntent.EditClosed -> closeEdit()
            is StorageSourceIntent.FileLongPressed -> editable.update {
                it.copy(editing = true, selected = it.selected + intent.id)
            }

            is StorageSourceIntent.FileToggled -> editable.update { it.toggled(intent.id) }
            StorageSourceIntent.AllToggled -> toggleAll()
            StorageSourceIntent.DeleteConfirmed -> state.value?.selected?.let { delete(it) }
            is StorageSourceIntent.FileClicked -> open(intent.id)
        }
    }

    private fun open(id: String) {
        val entry = entries.value.find { it.fileId == id } ?: return
        viewModelScope.launch { filesProviderHandler.onItemClick(entry) }
    }

    private fun setGrouped(grouped: Boolean) {
        viewModelScope.launch { appSettings.setStorageGroupedByFolder(key.sourceId, grouped) }
    }

    private fun closeEdit() {
        editable.update { it.copy(editing = false, selected = emptySet()) }
    }

    private fun toggleAll() {
        val current = state.value ?: return
        val selected =
            if (current.allSelected) emptySet()
            else current.files.mapTo(mutableSetOf()) { it.id }

        editable.update { it.copy(selected = selected) }
    }

    private fun delete(ids: Set<String>) {
        if (ids.isEmpty()) return
        // TODO: delete the selected files from this phone through the engine.
        closeEdit()
    }
}

private data class Editable(
    val sort: SortUi = SortUi.Size,
    val editing: Boolean = false,
    val selected: Set<String> = emptySet(),
) {
    fun toggled(id: String): Editable =
        copy(selected = if (id in selected) selected - id else selected + id)
}

private fun SourceEntry.path(): String? =
    if (role == SourceEntry.Role.Initiator) originPath else location.readablePath()

private fun SyncFileEntry.toUi(sending: Set<String>): FileBrowserUi.File = asPreviewFile().copy(
    sync = when {
        remoteState is LocalIndexedFile.State.Present -> null
        fileId in sending -> FileBrowserUi.File.Sync.Sending
        else -> FileBrowserUi.File.Sync.Waiting
    },
)

private val SortUi.fileSort: FileSortUi
    get() = when (this) {
        SortUi.Size -> FileSortUi.Size
        SortUi.Date -> FileSortUi.Date
        SortUi.Name -> FileSortUi.Name
    }

private val SortUi.ascending: Boolean
    get() = this == SortUi.Name

private val SortUi.comparator: Comparator<FileBrowserUi.File>
    get() = when (this) {
        SortUi.Size -> compareByDescending { it.size?.bytes }
        SortUi.Date -> compareByDescending { it.modifiedAt }
        SortUi.Name -> compareBy { it.name.lowercase() }
    }
