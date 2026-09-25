package com.fserver.app.presentation.screens.source.shared.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.source.shared.model.nameOf
import com.fserver.app.presentation.screens.source.shared.model.toUi
import com.fserver.app.presentation.screens.source.shared.progress.model.SourceProgressState
import com.fserver.app.presentation.screens.source.shared.progress.model.SourceProgressUiEffect
import com.fserver.app.presentation.shared.error.ErrorReporter
import com.fserver.core.storage.RegisteredSourcesRepository
import com.fserver.core.storage.TrustedDevicesRepository
import com.fserver.core.sync.SourcesController
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.progress.FileTransfer
import com.fserver.core.sync.progress.SourcePass
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SourceProgressViewModel(
    private val key: Destination.Source.Progress,
    private val sourcesRepository: RegisteredSourcesRepository,
    private val sourcesController: SourcesController,
    private val trustedDevices: TrustedDevicesRepository,
    private val reporter: ErrorReporter,
) : ViewModel() {

    private val effects = Channel<SourceProgressUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    private var syncJob: Job? = null

    private val entry = sourcesRepository.observeById(key.sourceId)

    private val pass = sourcesController.progress.pass(key.sourceId)

    private val transfers = sourcesController.progress.transfers

    val state: StateFlow<SourceProgressState> = combine(
        entry,
        pass,
        transfers,
        trustedDevices.devices,
    ) { source, pass, transfers, devices ->
        source ?: return@combine SourceProgressState()

        val local = pass as? SourcePass.Local
        val refusal = (source.status as? SourceEntry.Status.Disabled)?.reason
        val moving = transfers.filter {
            it.key.sourceId == key.sourceId && pass != null && it.startedAt >= pass.startedAt
        }

        SourceProgressState(
            role = source.role.toUi(),
            phase = when {
                refusal != null -> SourceProgressState.Phase.Refused
                pass != null -> SourceProgressState.Phase.Syncing
                else -> SourceProgressState.Phase.Waiting
            },
            peerName = devices.nameOf(source.deviceId),
            sourceLabel = source.label,
            progress = local?.progress,
            actionsPlanned = local?.actionsPlanned ?: 0,
            actionsDone = local?.actionsDone ?: 0,
            isCounted = local != null,
            isPlanned = local != null && local.stage != SourcePass.Local.Stage.Scanning,
            filesDone = moving.count { it.state == FileTransfer.State.Completed },
            filesTotal = moving.size,
            reason = refusal,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SourceProgressState(),
    )

    init {
        startSync()

        viewModelScope.launch {
            pass.collect { pass ->
                if (pass?.isFinished == true) effects.send(SourceProgressUiEffect.NavigateToDone)
            }
        }
    }

    private fun startSync() {
        if (syncJob != null) return

        syncJob = viewModelScope.launch {
            runCatching { sourcesController.runSync() }
                .exceptionOrNull()
                ?.let { reporter.report(it, "First pass failed") }
        }
    }
}
