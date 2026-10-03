package com.fserver.app.presentation.screens.settings.storage.source

import com.fserver.app.presentation.shared.browser.model.key
import com.fserver.app.presentation.shared.browser.model.FileKey
import com.fserver.app.util.stateInScreen
import com.fserver.app.presentation.shared.selection.Selection
import com.fserver.app.presentation.screens.source.shared.model.ownHalfOf
import com.fserver.app.presentation.screens.source.shared.model.storageLabel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.data.AppSettingsStore
import com.fserver.app.presentation.composable.model.direction
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.files.shared.FilesProviderHandler
import com.fserver.app.presentation.composable.model.peerOf
import com.fserver.app.presentation.composable.model.peers
import com.fserver.app.presentation.screens.settings.storage.source.model.StorageSourceIntent
import com.fserver.app.presentation.screens.settings.storage.source.model.StorageSourceState
import com.fserver.app.presentation.screens.settings.storage.source.model.StorageSourceState.FreeBlockUi
import com.fserver.app.presentation.screens.settings.storage.source.model.StorageSourceState.RefusalUi
import com.fserver.app.presentation.screens.settings.storage.source.model.StorageSourceState.SortUi
import com.fserver.app.presentation.screens.settings.storage.source.model.StorageSourceUiEffect
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.shared.browser.model.FileSortUi
import com.fserver.app.presentation.shared.browser.model.asPreviewFile
import com.fserver.app.presentation.shared.browser.model.toTree
import com.fserver.app.presentation.shared.error.ErrorReporter
import com.fserver.app.util.combineMany
import com.fserver.core.files.EvictRefusal
import com.fserver.core.files.FilesController
import com.fserver.core.files.SyncFileEntry
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.storage.RegisteredSourcesRepository
import com.fserver.core.sync.SourcesController
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.evictsByHand
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
    private val filesController: FilesController,
    sourcesController: SourcesController,
    devicesRepository: DevicesRepository,
    private val appSettings: AppSettingsStore,
    private val reporter: ErrorReporter,
) : ViewModel() {
    private val effects = Channel<StorageSourceUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    private val filesProviderHandler = FilesProviderHandler(
        filesController = filesController,
        registeredSourcesRepository = registeredSources,
        progress = sourcesController.progress,
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
        devicesRepository.peers(),
        appSettings.storageGroupedByFolder(key.sourceId),
        editable,
        registeredSources.metadata,
    ) { source, entries, transfers, peers, grouped, editable, metadata ->
        if (source == null) return@combineMany null

        val sending = transfers
            .filter { !it.isFinished && it.key.sourceId == source.id }
            .associate { it.key.fileId to it.progress }
        val here = entries.filter { it.localState is LocalIndexedFile.State.Present }
        val fileOf = { entry: SyncFileEntry -> entry.toUi(sending) }
        val files = here.map(fileOf).sortedWith(editable.sort.comparator)
        val keys = files.mapTo(mutableSetOf()) { it.indexedKey }
        val hasFolders = here.any { '/' in it.path }
        val freeBlock = source.freeBlock()

        StorageSourceState(
            isLoading = false,
            label = source.label,
            path = metadata.ownHalfOf(source)?.storageLabel(),
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
            editing = editable.selection.active && files.isNotEmpty() && freeBlock == null,
            selected = editable.selection.ids intersect keys,
            freeBlock = freeBlock,
            refusals = here.mapNotNull { entry -> entry.evictRefusal?.let { entry.key to it.toUi() } }.toMap(),
        )
    }.stateInScreen(viewModelScope, null)

    fun onIntent(intent: StorageSourceIntent) {
        when (intent) {
            is StorageSourceIntent.SortChanged -> editable.update { it.copy(sort = intent.sort) }
            is StorageSourceIntent.GroupingChanged -> setGrouped(intent.grouped)
            StorageSourceIntent.EditStarted -> startEdit { it.copy(active = true) }
            StorageSourceIntent.EditClosed -> closeEdit()
            is StorageSourceIntent.FileLongPressed -> startEdit { it.started(intent.file) }

            is StorageSourceIntent.FileToggled -> editable.update {
                it.copy(selection = it.selection.toggled(intent.file))
            }
            StorageSourceIntent.AllToggled -> toggleAll()
            StorageSourceIntent.FreeConfirmed -> state.value?.freeable?.let { free(it) }
            is StorageSourceIntent.FileClicked -> open(intent.file)
        }
    }

    private fun open(file: FileKey) {
        val entry = entries.value.find { it.key == file } ?: return
        viewModelScope.launch { filesProviderHandler.onItemClick(entry) }
    }

    private fun setGrouped(grouped: Boolean) {
        viewModelScope.launch { appSettings.setStorageGroupedByFolder(key.sourceId, grouped) }
    }

    private fun startEdit(select: (Selection<FileKey>) -> Selection<FileKey>) {
        if (state.value?.canFree != true) return
        editable.update { it.copy(selection = select(it.selection)) }
    }

    private fun closeEdit() {
        editable.update { it.copy(selection = Selection()) }
    }

    private fun toggleAll() {
        val current = state.value ?: return
        val selected =
            if (current.allSelected) emptySet()
            else current.files.mapTo(mutableSetOf()) { it.indexedKey }

        editable.update { it.copy(selection = it.selection.copy(ids = selected)) }
    }

    private fun free(files: Set<FileKey>) {
        closeEdit()
        val ids = files.filter { it.sourceId == key.sourceId }.mapTo(mutableSetOf()) { it.fileId }
        if (ids.isEmpty()) return

        viewModelScope.launch {
            filesController.evict(key.sourceId, ids)
                .onFailure { reporter.report(it, "could not evict ${ids.size} files of ${key.sourceId}") }
        }
    }
}

private data class Editable(
    val sort: SortUi = SortUi.Size,
    val selection: Selection<FileKey> = Selection(),
)

private fun SyncFileEntry.toUi(sending: Map<String, Float?>): FileBrowserUi.File = asPreviewFile().copy(
    sync = when {
        remoteState is LocalIndexedFile.State.Present -> null
        fileId in sending -> FileBrowserUi.File.Sync.Sending(sending[fileId])
        else -> FileBrowserUi.File.Sync.Waiting
    },
)

private fun SourceEntry.freeBlock(): FreeBlockUi? = when {
    evictsByHand -> null
    status != SourceEntry.Status.Active -> FreeBlockUi.Inactive
    else -> FreeBlockUi.Keeper
}

private fun EvictRefusal.toUi(): RefusalUi = when (this) {
    EvictRefusal.NotHere, EvictRefusal.NotOnPeer -> RefusalUi.NotOnPeer
    EvictRefusal.PeerDiffers -> RefusalUi.PeerDiffers
    EvictRefusal.Unverified, EvictRefusal.Unmerged -> RefusalUi.Unverified
    EvictRefusal.Pinned -> RefusalUi.Pinned
}

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
