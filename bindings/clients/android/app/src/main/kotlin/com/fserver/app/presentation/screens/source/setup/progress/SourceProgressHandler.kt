package com.fserver.app.presentation.screens.source.setup.progress

import com.fserver.app.presentation.composable.model.peerOf
import com.fserver.app.presentation.composable.model.peers
import com.fserver.app.presentation.screens.source.setup.progress.model.SourceProgressState
import com.fserver.app.presentation.screens.source.setup.progress.model.SourceProgressUiEffect
import com.fserver.app.presentation.screens.source.setup.shared.model.SourceSetupState
import com.fserver.app.presentation.screens.source.shared.model.toUi
import com.fserver.app.presentation.screens.source.shared.model.transfersOf
import com.fserver.app.presentation.shared.error.ErrorReporter
import com.fserver.app.presentation.shared.sync.SyncTrigger
import com.fserver.app.util.stateInScreen
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.storage.RegisteredSourcesRepository
import com.fserver.core.sync.SourcesController
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.progress.FileTransfer
import com.fserver.core.sync.progress.SourcePass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SourceProgressHandler(
    private val sourcesRepository: RegisteredSourcesRepository,
    private val sourcesController: SourcesController,
    private val devicesRepository: DevicesRepository,
    reporter: ErrorReporter,

    flow: Flow<SourceSetupState>,
    scope: CoroutineScope,
) {
    private val effectChannel = Channel<SourceProgressUiEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private val syncTrigger = SyncTrigger(scope, sourcesController, reporter)

    private val sourceId = flow.map { it.sourceId }.distinctUntilChanged()

    val state: StateFlow<SourceProgressState?> = sourceId
        .flatMapLatest { id -> if (id == null) flowOf(null) else stateOf(id) }
        .stateInScreen(scope, null)

    init {
        scope.launch {
            sourceId.collectLatest { id ->
                id ?: return@collectLatest

                syncTrigger.run("First pass failed")
                sourcesController.progress.pass(id).first { it?.isFinished == true }
                effectChannel.send(SourceProgressUiEffect.NavigateToDone)
            }
        }
    }

    private fun stateOf(sourceId: String): Flow<SourceProgressState> = combine(
        sourcesRepository.observeById(sourceId),
        sourcesController.progress.pass(sourceId),
        sourcesController.progress.transfers,
        devicesRepository.peers(),
        sourcesController.progress.indexing(sourceId),
    ) { source, pass, transfers, peers, indexing ->
        source ?: return@combine SourceProgressState()

        val local = pass as? SourcePass.Local
        val refusal = (source.status as? SourceEntry.Status.Disabled)?.reason
        val moving = pass.transfersOf(sourceId, transfers)

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
            filesTotal = if (pass is SourcePass.Remote) 0 else moving.size,
            filesSkipped = local?.filesSkipped ?: 0,
            reason = refusal,
        )
    }
}

private val PrePlanStages = setOf(SourcePass.Local.Stage.Scanning, SourcePass.Local.Stage.Hashing)
