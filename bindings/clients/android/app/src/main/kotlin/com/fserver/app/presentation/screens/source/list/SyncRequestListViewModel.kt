package com.fserver.app.presentation.screens.source.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.error.ErrorReporter
import com.fserver.app.presentation.screens.source.list.model.SyncRequestListIntent
import com.fserver.app.presentation.screens.source.list.model.SyncRequestListState
import com.fserver.app.presentation.screens.source.list.model.SyncRequestListUiEffect
import com.fserver.app.presentation.screens.source.request.shared.model.toUi
import com.fserver.core.storage.TrustedDevicesRepository
import com.fserver.core.sync.SourcesController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SyncRequestListViewModel(
    private val sourcesController: SourcesController,
    private val trustedDevices: TrustedDevicesRepository,
    private val reporter: ErrorReporter,
) : ViewModel() {

    private val effects = Channel<SyncRequestListUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    private val answering = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            sourcesController.incomingRequests
                .map { it.isEmpty() }
                .distinctUntilChanged()
                .drop(1)
                .filter { it }
                .collect { effects.send(SyncRequestListUiEffect.Close) }
        }
    }

    val state: StateFlow<SyncRequestListState> = combine(
        sourcesController.incomingRequests,
        trustedDevices.devices,
        answering,
    ) { requests, devices, isAnswering ->
        SyncRequestListState(
            requests = requests.sortedByDescending { it.receivedAt }.map { it.toUi(devices) },
            isAnswering = isAnswering,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SyncRequestListState(),
    )

    fun onIntent(intent: SyncRequestListIntent) {
        when (intent) {
            is SyncRequestListIntent.Declined -> decline(listOf(intent.sourceId))
            SyncRequestListIntent.DeclinedAll -> decline(state.value.requests.map { it.sourceId })
        }
    }

    private fun decline(sourceIds: List<String>) {
        if (answering.value || sourceIds.isEmpty()) return
        answering.value = true

        viewModelScope.launch {
            sourceIds.forEach { sourceId ->
                sourcesController.rejectRequest(sourceId).exceptionOrNull()?.let { failure ->
                    reporter.report(failure, "Could not decline source $sourceId")
                }
            }

            answering.value = false
        }
    }
}
