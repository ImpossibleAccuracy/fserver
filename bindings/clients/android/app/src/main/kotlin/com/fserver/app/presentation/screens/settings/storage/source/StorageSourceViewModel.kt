package com.fserver.app.presentation.screens.settings.storage.source

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.data.AppSettingsStore
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.settings.storage.main.model.peerOf
import com.fserver.app.presentation.screens.settings.storage.main.model.peers
import com.fserver.app.presentation.composable.model.direction
import com.fserver.app.presentation.screens.settings.storage.source.model.StorageSourceIntent
import com.fserver.app.presentation.screens.settings.storage.source.model.StorageSourceState
import com.fserver.app.presentation.screens.settings.storage.source.model.StorageSourceState.CopyStatusUi
import com.fserver.app.presentation.screens.settings.storage.source.model.StorageSourceState.FileUi
import com.fserver.app.presentation.screens.settings.storage.source.model.StorageSourceState.GroupUi
import com.fserver.app.presentation.screens.settings.storage.source.model.StorageSourceState.SortUi
import com.fserver.app.presentation.screens.source.shared.model.readablePath
import com.fserver.app.presentation.shared.browser.model.asPreviewFile
import com.fserver.app.util.combineMany
import com.fserver.core.files.FilesController
import com.fserver.core.files.SyncFileEntry
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.storage.RegisteredSourcesRepository
import com.fserver.core.storage.TrustedDevicesRepository
import com.fserver.core.sync.SourcesController
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.model.SourceEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
) : ViewModel() {
    private val editable = MutableStateFlow(Editable())

    val state: StateFlow<StorageSourceState> = combineMany(
        registeredSources.observeById(key.sourceId),
        filesController.overallContent,
        sourcesController.progress.transfers,
        peers(trustedDevices, devicesRepository),
        appSettings.storageGroupedByFolder(key.sourceId),
        editable,
    ) { source, entries, transfers, peers, grouped, editable ->
        if (source == null) return@combineMany StorageSourceState(isLoading = false, exists = false)

        val all = entries.filter { it.sourceId == source.id }
        val sending = transfers
            .filter { !it.isFinished && it.key.sourceId == source.id }
            .mapTo(mutableSetOf()) { it.key.fileId }
        val files = all
            .filter { it.localState is LocalIndexedFile.State.Present }
            .map { it.toUi(sending) }
            .sortedWith(editable.sort.comparator)
        val ids = files.mapTo(mutableSetOf()) { it.id }

        StorageSourceState(
            isLoading = false,
            label = source.label,
            path = source.path(),
            peer = peers.peerOf(source.deviceId),
            direction = source.direction(),
            offPhoneFiles = all.size - files.size,
            sort = editable.sort,
            grouped = grouped,
            groups = if (grouped) files.groupedByFolder(editable.sort) else listOf(GroupUi(null, files)),
            editing = editable.editing && files.isNotEmpty(),
            selected = editable.selected intersect ids,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StorageSourceState(),
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
            StorageSourceIntent.DeleteConfirmed -> delete(state.value.selected)
        }
    }

    private fun setGrouped(grouped: Boolean) {
        viewModelScope.launch { appSettings.setStorageGroupedByFolder(key.sourceId, grouped) }
    }

    private fun closeEdit() {
        editable.update { it.copy(editing = false, selected = emptySet()) }
    }

    private fun toggleAll() {
        val current = state.value
        val selected = if (current.allSelected) emptySet() else current.files.mapTo(mutableSetOf()) { it.id }
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

private fun SyncFileEntry.toUi(sending: Set<String>) = FileUi(
    id = fileId,
    folder = path.substringBeforeLast('/', missingDelimiterValue = "").ifEmpty { null },
    status = when {
        remoteState is LocalIndexedFile.State.Present -> CopyStatusUi.OnPeer
        fileId in sending -> CopyStatusUi.Sending
        else -> CopyStatusUi.Waiting
    },
    preview = asPreviewFile(),
)

private val SortUi.comparator: Comparator<FileUi>
    get() = when (this) {
        SortUi.Size -> compareByDescending { it.bytes }
        SortUi.Date -> compareByDescending { it.preview.modifiedAt }
        SortUi.Name -> compareBy { it.name.lowercase() }
    }

private fun List<FileUi>.groupedByFolder(sort: SortUi): List<GroupUi> {
    val groups = groupBy { it.folder }.map { (folder, files) -> GroupUi("/" + folder.orEmpty(), files) }

    return when (sort) {
        SortUi.Size -> groups.sortedByDescending { it.bytes }
        SortUi.Date -> groups.sortedByDescending { group -> group.files.mapNotNull { it.preview.modifiedAt }.maxOrNull() }
        SortUi.Name -> groups.sortedBy { it.folder.orEmpty().lowercase() }
    }
}
