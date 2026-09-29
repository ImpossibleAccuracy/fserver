package com.fserver.app.presentation.screens.activity

import com.fserver.app.util.stateInScreen
import com.fserver.core.network.device.DevicesRepository
import com.fserver.app.presentation.shared.sync.SyncTrigger
import com.fserver.app.presentation.composable.model.fileName
import com.fserver.app.presentation.composable.model.peers
import com.fserver.app.presentation.composable.model.peerOf
import com.fserver.app.presentation.composable.model.PeerUi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.composable.model.TransferUi
import com.fserver.app.presentation.screens.activity.model.ActivityIntent
import com.fserver.app.presentation.screens.activity.model.ActivityState
import com.fserver.app.presentation.screens.source.request.shared.model.toUi
import com.fserver.app.presentation.shared.error.ErrorReporter
import com.fserver.common.model.FileSize
import com.fserver.core.storage.TrustedDevicesRepository
import com.fserver.core.sync.SourcesController
import com.fserver.core.sync.conflict.ConflictDecision
import com.fserver.core.sync.conflict.ConflictsController
import com.fserver.core.sync.conflict.FileConflict
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.progress.FileTransfer
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ActivityViewModel(
    private val sourcesController: SourcesController,
    private val conflictsController: ConflictsController,
    private val trustedDevices: TrustedDevicesRepository,
    devicesRepository: DevicesRepository,
    private val reporter: ErrorReporter,
) : ViewModel() {

    private val syncTrigger = SyncTrigger(viewModelScope, sourcesController, reporter)

    val state: StateFlow<ActivityState> = combine(
        sourcesController.progress.transfers,
        sourcesController.incomingRequests,
        devicesRepository.peers(),
        trustedDevices.devices,
        conflictsController.pending,
    ) { transfers, requests, peers, trusted, conflicts ->
        ActivityState(
            freedLabel = SampleFreed,
            quotaLabel = SampleQuota,
            syncRequest = requests.maxByOrNull { it.receivedAt }?.toUi(peers, trusted),
            syncRequestsWaiting = requests.size,
            conflicts = conflicts.map { it.toUi(peers) },
            running = transfers.map { it.toUi() }.filterNot { it is TransferUi.Completed },
            history = ActivityState.SampleHistory,
        )
    }.stateInScreen(
        viewModelScope,
        ActivityState(
            freedLabel = SampleFreed,
            quotaLabel = SampleQuota,
            history = ActivityState.SampleHistory,
        ),
    )

    fun onIntent(intent: ActivityIntent) {
        when (intent) {
            ActivityIntent.ClearClicked -> sourcesController.progress.clearFinished()
            is ActivityIntent.RetryClicked -> syncTrigger.run("Retry pass failed")
            is ActivityIntent.ConflictKeepMineClicked -> resolve(intent.conflictId, ConflictDecision.Choice.KeepLocal)
            is ActivityIntent.ConflictKeepTheirsClicked -> resolve(intent.conflictId, ConflictDecision.Choice.KeepRemote)
            is ActivityIntent.ConflictKeepBothClicked -> resolve(intent.conflictId, ConflictDecision.Choice.KeepBoth)
            is ActivityIntent.UndoClicked -> Unit
            ActivityIntent.FullHistoryClicked -> Unit
        }
    }

    private fun resolve(conflictId: String, choice: ConflictDecision.Choice) {
        val conflict = conflictsController.pending.value.firstOrNull { it.uiId == conflictId } ?: return

        viewModelScope.launch {
            conflictsController.resolve(conflict, choice)
                .exceptionOrNull()
                ?.let { reporter.report(it, "Could not resolve conflict on ${conflict.path}") }
        }
    }

    private fun FileConflict.toUi(peers: Map<String, PeerUi>) = ActivityState.ConflictUi(
        id = uiId,
        fileName = path.fileName(),
        peerName = peers.peerOf(remote.deviceId).name,
        change = when {
            local.state is LocalIndexedFile.State.Deleted -> ActivityState.ChangeUi.DeletedHere
            remote.state is LocalIndexedFile.State.Deleted -> ActivityState.ChangeUi.DeletedThere
            else -> ActivityState.ChangeUi.EditedBoth
        },
        canKeepMine = ConflictDecision.Choice.KeepLocal in choices,
        canKeepTheirs = ConflictDecision.Choice.KeepRemote in choices,
        canKeepBoth = ConflictDecision.Choice.KeepBoth in choices,
    )

    private val FileConflict.uiId: String
        get() = "$sourceId/$fileId"

    private fun FileTransfer.toUi(): TransferUi {
        val fileName = path.fileName()

        return when (state) {
            FileTransfer.State.Queued -> TransferUi.Queued(
                id = key.id,
                fileName = fileName,
                direction = key.direction,
            )

            FileTransfer.State.Running -> TransferUi.Running(
                id = key.id,
                fileName = fileName,
                direction = key.direction,
                progress = progress,
                transferred = FileSize(transferredBytes),
                total = FileSize(totalBytes),
                bytesPerSecond = bytesPerSecond,
                eta = eta,
            )

            FileTransfer.State.Completed -> TransferUi.Completed(
                id = key.id,
                fileName = fileName,
                direction = key.direction,
            )

            is FileTransfer.State.Failed -> TransferUi.Interrupted(
                id = key.id,
                fileName = fileName,
                direction = key.direction,
                stoppedAtPercent = ((progress ?: 0f) * 100).toInt(),
            )
        }
    }

    private companion object {
        const val SampleFreed = "12.4 GB"
        const val SampleQuota = "61 %"
    }
}
