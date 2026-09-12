package com.fserver.app.presentation.screens.files.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.composable.model.FileAvailabilityUi
import com.fserver.app.presentation.screens.files.list.model.FilesIntent
import com.fserver.app.presentation.screens.files.list.model.FilesState
import com.fserver.app.presentation.screens.source.request.shared.model.toUi
import com.fserver.core.storage.TrustedDevicesRepository
import com.fserver.core.sync.SourcesController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class FilesViewModel(
    private val sourcesController: SourcesController,
    private val trustedDevices: TrustedDevicesRepository,
) : ViewModel() {

    private data class Editable(
        val selectedDeviceId: String? = null,
        val filter: FilesState.FilterUi = FilesState.FilterUi.All,
        val expandedDeviceId: String? = null,
        val syncRequestHintDismissed: Boolean = false,
    )

    private val editable = MutableStateFlow(Editable())

    val state: StateFlow<FilesState> = combine(
        editable,
        sourcesController.incomingRequests,
        trustedDevices.devices,
    ) { edit, requests, devices ->
        FilesState(
            devices = FilesState.SampleDevices,
            selectedDeviceId = edit.selectedDeviceId,
            filter = edit.filter,
            entries = FilesState.SampleEntries.matching(edit.filter),
            expandedDevice = FilesState.SampleDevices
                .firstOrNull { it.id == edit.expandedDeviceId }
                ?.let(FilesState::sampleDetailsOf),
            syncRequest = requests.maxByOrNull { it.receivedAt }?.toUi(devices),
            syncRequestsWaiting = requests.size,
            syncRequestHintDismissed = edit.syncRequestHintDismissed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FilesState(
            devices = FilesState.SampleDevices,
            entries = FilesState.SampleEntries,
        ),
    )

    fun onIntent(intent: FilesIntent) {
        when (intent) {
            is FilesIntent.DeviceClicked -> editable.update {
                it.copy(selectedDeviceId = intent.deviceId.takeIf { id -> id != it.selectedDeviceId })
            }

            is FilesIntent.DeviceExpanded -> editable.update {
                it.copy(expandedDeviceId = intent.deviceId)
            }

            FilesIntent.DeviceCollapsed -> editable.update { it.copy(expandedDeviceId = null) }

            is FilesIntent.FilterSelected -> editable.update { it.copy(filter = intent.filter) }

            FilesIntent.FilterCleared -> editable.update {
                it.copy(selectedDeviceId = null, filter = FilesState.FilterUi.All)
            }

            FilesIntent.SyncRequestHintDismissed -> editable.update {
                it.copy(syncRequestHintDismissed = true)
            }

            is FilesIntent.EntryClicked -> Unit
            FilesIntent.SearchClicked -> Unit
        }
    }
}

private fun List<FilesState.EntryUi>.matching(
    filter: FilesState.FilterUi,
): List<FilesState.EntryUi> = when (filter) {
    FilesState.FilterUi.All -> this
    FilesState.FilterUi.Local -> filter {
        it.isFolder || it.file.availability == FileAvailabilityUi.OnDevice
    }

    FilesState.FilterUi.Cloud -> filter {
        it.isFolder || it.file.availability != FileAvailabilityUi.OnDevice
    }
}
