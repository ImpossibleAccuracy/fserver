package com.fserver.app.presentation.screens.files.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.screens.files.list.model.FilesIntent
import com.fserver.app.presentation.screens.files.list.model.FilesState
import com.fserver.app.presentation.screens.files.list.model.FilesUiEffect
import com.fserver.app.presentation.screens.files.shared.FilesProviderHandler
import com.fserver.app.presentation.screens.source.request.shared.model.toUi
import com.fserver.app.presentation.screens.source.shared.preview.model.SourcePreviewUi
import com.fserver.app.presentation.screens.source.shared.preview.model.asPreviewFile
import com.fserver.core.files.FilesController
import com.fserver.core.storage.TrustedDevicesRepository
import com.fserver.core.sync.SourcesController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
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

@OptIn(ExperimentalCoroutinesApi::class)
class FilesViewModel(
    private val sourcesController: SourcesController,
    private val trustedDevices: TrustedDevicesRepository,
    private val filesController: FilesController,
) : ViewModel() {
    private val effects = Channel<FilesUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    private val filesProviderHandler = FilesProviderHandler(
        filesController = filesController,
        openFile = {
            viewModelScope.launch {
                effects.send(FilesUiEffect.OpenFile(it.asPreviewFile()))
            }
        }
    )

    private val editable = MutableStateFlow(Editable())

    private val entries = editable.map { it.filter }
        .distinctUntilChanged()
        .flatMapLatest {
            filesProviderHandler.loadPreviewFiles(
                requiredLocation = when (it) {
                    FilesState.FilterUi.All -> null
                    FilesState.FilterUi.Local -> SourcePreviewUi.File.Location.Local
                    FilesState.FilterUi.Cloud -> SourcePreviewUi.File.Location.Remote
                }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = null,
        )

    val state: StateFlow<FilesState> = combine(
        editable,
        entries,
        sourcesController.incomingRequests,
        trustedDevices.devices,
    ) { edit, files, requests, devices ->

        FilesState(
            devices = FilesState.SampleDevices,
            selectedDeviceId = edit.selectedDeviceId,
            filter = edit.filter,
            entries = files?.let { SourcePreviewUi.PlainList(it) },
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
        initialValue = FilesState(),
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

            is FilesIntent.EntryClicked -> {
                viewModelScope.launch {
                    val file = filesController.overallContent.value
                        .find { it.fileId == intent.entryId }
                        ?: return@launch

                    filesProviderHandler.onItemClick(file)
                }
            }

            FilesIntent.SearchClicked -> Unit
        }
    }

    private data class Editable(
        val selectedDeviceId: String? = null,
        val filter: FilesState.FilterUi = FilesState.FilterUi.All,
        val expandedDeviceId: String? = null,
        val syncRequestHintDismissed: Boolean = false,
    )
}
