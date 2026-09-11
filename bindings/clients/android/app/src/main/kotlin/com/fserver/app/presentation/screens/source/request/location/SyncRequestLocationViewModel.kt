package com.fserver.app.presentation.screens.source.request.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.source.request.location.model.SyncRequestLocationIntent
import com.fserver.app.presentation.screens.source.request.location.model.SyncRequestLocationState
import com.fserver.app.presentation.screens.source.request.location.model.SyncRequestLocationUiEffect
import com.fserver.app.presentation.screens.source.shared.model.HostLocationUi
import com.fserver.app.presentation.screens.source.shared.model.toLocation
import com.fserver.app.presentation.screens.source.request.shared.model.toUi
import com.fserver.core.storage.TrustedDevicesRepository
import com.fserver.core.sync.SourcesController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class SyncRequestLocationViewModel(
    private val key: Destination.Source.Request.Location,
    private val sourcesController: SourcesController,
    private val trustedDevices: TrustedDevicesRepository,
) : ViewModel() {

    private val editable = MutableStateFlow(Editable())

    private val effects = Channel<SyncRequestLocationUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    val state: StateFlow<SyncRequestLocationState> = combine(
        sourcesController.incomingRequests,
        trustedDevices.devices,
        editable,
    ) { requests, devices, local ->
        SyncRequestLocationState(
            request = requests.firstOrNull { it.sourceId == key.sourceId }?.toUi(devices),
            isLoaded = true,
            selected = local.selected,
            folder = local.folder,
            isAccepting = local.accepting,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SyncRequestLocationState(),
    )

    fun onIntent(intent: SyncRequestLocationIntent) {
        when (intent) {
            SyncRequestLocationIntent.AppStorageSelected ->
                editable.update { it.copy(selected = HostLocationUi.AppStorage) }

            is SyncRequestLocationIntent.FolderPicked -> {
                val folder = HostLocationUi.Folder(uri = intent.uri, label = intent.label)
                editable.update { it.copy(selected = folder, folder = folder) }
            }

            SyncRequestLocationIntent.Accepted -> accept()
        }
    }

    private fun accept() {
        if (editable.value.accepting) return
        editable.update { it.copy(accepting = true) }

        viewModelScope.launch {
            sourcesController.acceptRequest(
                sourceId = key.sourceId,
                location = editable.value.selected.toLocation(key.sourceId),
            ).fold(
                onSuccess = { effects.send(SyncRequestLocationUiEffect.NavigateToProgress) },
                onFailure = { failure ->
                    Timber.e(failure, "Could not accept source ${key.sourceId}")
                    effects.send(
                        SyncRequestLocationUiEffect.ShowMessage(
                            failure.message ?: failure.toString()
                        )
                    )
                },
            )

            editable.update { it.copy(accepting = false) }
        }
    }

    private data class Editable(
        val selected: HostLocationUi = HostLocationUi.AppStorage,
        val folder: HostLocationUi.Folder? = null,
        val accepting: Boolean = false,
    )
}
