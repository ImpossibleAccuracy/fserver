package com.fserver.app.presentation.screens.source.shared.progress

import com.fserver.app.util.stateInScreen
import com.fserver.core.network.device.DevicesRepository
import com.fserver.app.presentation.screens.source.shared.model.transfersOf
import com.fserver.app.presentation.shared.sync.SyncTrigger
import com.fserver.app.presentation.composable.model.peers
import com.fserver.app.presentation.composable.model.peerOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.source.shared.model.toUi
import com.fserver.app.presentation.screens.source.shared.progress.model.SourceProgressState
import com.fserver.app.presentation.screens.source.shared.progress.model.SourceProgressUiEffect
import com.fserver.app.presentation.shared.error.ErrorReporter
import com.fserver.core.storage.RegisteredSourcesRepository
import com.fserver.core.sync.SourcesController
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.progress.FileTransfer
import com.fserver.core.sync.progress.SourcePass
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class SourceProgressViewModel(
    private val key: Destination.Source.Progress,
    private val sourcesRepository: RegisteredSourcesRepository,
    private val sourcesController: SourcesController,
    devicesRepository: DevicesRepository,
    private val reporter: ErrorReporter,
) : ViewModel() {

    private val effects = Channel<SourceProgressUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    private val syncTrigger = SyncTrigger(viewModelScope, sourcesController, reporter)

    private val entry = sourcesRepository.observeById(key.sourceId)

    private val pass = sourcesController.progress.pass(key.sourceId)

    private val transfers = sourcesController.progress.transfers

    private val indexing = sourcesController.progress.indexing(key.sourceId)

    val state: StateFlow<SourceProgressState> = combine(
        entry,
        pass,
        transfers,
        devicesRepository.peers(),
        indexing,
    ) { source, pass, transfers, peers, indexing ->
        source ?: return@combine SourceProgressState()

        val local = pass as? SourcePass.Local
        val refusal = (source.status as? SourceEntry.Status.Disabled)?.reason
        val moving = pass.transfersOf(key.sourceId, transfers)

        SourceProgressState(
            role = source.role.toUi(),
            phase = when {
                refusal != null -> SourceProgressState.Phase.Refused
                pass != null -> SourceProgressState.Phase.Syncing
                else -> SourceProgressState.Phase.Waiting
            },
            peerName = peers.peerOf(source.deviceId).name,
            sourceLabel = source.label,
            progress = local?.progress,
            actionsPlanned = local?.actionsPlanned ?: 0,
            actionsDone = local?.actionsDone ?: 0,
            isCounted = local != null,
            isPlanned = local != null && local.stage !in PrePlanStages,
            isHashing = local?.stage == SourcePass.Local.Stage.Hashing,
            filesHashed = indexing?.filesHashed ?: 0,
            filesToHash = indexing?.filesToHash ?: 0,
            filesDone = moving.count { it.state == FileTransfer.State.Completed },
            filesTotal = moving.size,
            filesSkipped = local?.filesSkipped ?: 0,
            reason = refusal,
        )
    }.stateInScreen(viewModelScope, SourceProgressState())

    init {
        syncTrigger.run("First pass failed")

        viewModelScope.launch {
            pass.collect { pass ->
                if (pass?.isFinished == true) effects.send(SourceProgressUiEffect.NavigateToDone)
            }
        }
    }
}

private val PrePlanStages = setOf(SourcePass.Local.Stage.Scanning, SourcePass.Local.Stage.Hashing)
