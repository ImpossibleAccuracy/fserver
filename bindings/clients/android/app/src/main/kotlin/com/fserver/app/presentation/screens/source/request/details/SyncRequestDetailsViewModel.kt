package com.fserver.app.presentation.screens.source.request.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.source.request.details.model.SyncRequestDetailsIntent
import com.fserver.app.presentation.screens.source.request.details.model.SyncRequestDetailsState
import com.fserver.app.presentation.screens.source.request.details.model.SyncRequestDetailsUiEffect
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
import kotlinx.coroutines.launch
import timber.log.Timber

class SyncRequestDetailsViewModel(
    private val key: Destination.Source.Request.Details,
    private val sourcesController: SourcesController,
    private val trustedDevices: TrustedDevicesRepository,
) : ViewModel() {

    private val answering = MutableStateFlow(false)

    private val effects = Channel<SyncRequestDetailsUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    val state: StateFlow<SyncRequestDetailsState> = combine(
        sourcesController.incomingRequests,
        trustedDevices.devices,
        answering,
    ) { requests, devices, isAnswering ->
        SyncRequestDetailsState(
            request = requests.firstOrNull { it.sourceId == key.sourceId }?.toUi(devices),
            isLoaded = true,
            isAnswering = isAnswering,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SyncRequestDetailsState(),
    )

    fun onIntent(intent: SyncRequestDetailsIntent) {
        when (intent) {
            SyncRequestDetailsIntent.Declined -> decline()
        }
    }

    private fun decline() {
        if (answering.value) return
        answering.value = true

        viewModelScope.launch {
            sourcesController.rejectRequest(key.sourceId).fold(
                onSuccess = { effects.send(SyncRequestDetailsUiEffect.NavigateBack) },
                onFailure = { failure ->
                    Timber.w(failure, "Could not decline source ${key.sourceId}")
                    effects.send(
                        SyncRequestDetailsUiEffect.ShowMessage(
                            failure.message ?: failure.toString()
                        )
                    )
                },
            )

            answering.value = false
        }
    }
}
