package com.fserver.app.presentation.screens.files.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.data.DemoContentSource
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

/**
 * The server's tree, in whichever of the three shapes the user picked.
 *
 * The list is the default: it is the only view showing availability and size at a glance.
 * The grid serves photo and video folders; the tree keeps the whole structure on screen at
 * the cost of a narrow touch target, so it stays opt-in.
 */
class FilesViewModel(
    private val content: DemoContentSource,
    private val sourcesController: SourcesController,
    private val trustedDevices: TrustedDevicesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        FilesState(
            serverName = content.serverName(),
            breadcrumb = content.breadcrumb(),
            files = content.files(),
            gridTiles = content.gridTiles(),
            tree = content.tree(),
            itemCount = content.itemCount(),
        )
    )

    val state: StateFlow<FilesState> = combine(
        _state,
        sourcesController.incomingRequests,
        trustedDevices.devices,
    ) { base, requests, devices ->
        base.copy(
            syncRequest = requests.maxByOrNull { it.receivedAt }?.toUi(devices),
            syncRequestsWaiting = requests.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _state.value,
    )

    fun onIntent(intent: FilesIntent) {
        when (intent) {
            is FilesIntent.ViewModeSelected ->
                _state.value = _state.value.copy(viewMode = intent.mode)

            // Tapping a remote file will queue a download once :core is wired in; folders
            // will descend. The system picker and search are not built yet either.
            is FilesIntent.FileClicked -> Unit
            FilesIntent.SearchClicked -> Unit
        }
    }
}
