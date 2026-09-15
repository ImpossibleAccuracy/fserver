package com.fserver.app.presentation.screens.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.error.ErrorReporter
import com.fserver.app.presentation.composable.model.TransferUi
import com.fserver.app.presentation.screens.activity.model.ActivityIntent
import com.fserver.app.presentation.screens.activity.model.ActivityState
import com.fserver.app.presentation.screens.source.request.shared.model.toUi
import com.fserver.common.model.FileSize
import com.fserver.core.storage.TrustedDevicesRepository
import com.fserver.core.sync.SourcesController
import com.fserver.core.sync.progress.FileTransfer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ActivityViewModel(
    private val sourcesController: SourcesController,
    private val trustedDevices: TrustedDevicesRepository,
    private val reporter: ErrorReporter,
) : ViewModel() {

    val state: StateFlow<ActivityState> = combine(
        sourcesController.progress.transfers,
        sourcesController.incomingRequests,
        trustedDevices.devices,
    ) { transfers, requests, devices ->
        ActivityState(
            freedLabel = SampleFreed,
            quotaLabel = SampleQuota,
            syncRequest = requests.maxByOrNull { it.receivedAt }?.toUi(devices),
            syncRequestsWaiting = requests.size,
            conflicts = ActivityState.SampleConflicts,
            running = transfers.map { it.toUi() }.filterNot { it is TransferUi.Completed },
            history = ActivityState.SampleHistory,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ActivityState(
            freedLabel = SampleFreed,
            quotaLabel = SampleQuota,
            conflicts = ActivityState.SampleConflicts,
            history = ActivityState.SampleHistory,
        ),
    )

    fun onIntent(intent: ActivityIntent) {
        when (intent) {
            ActivityIntent.ClearClicked -> sourcesController.progress.clearFinished()
            is ActivityIntent.RetryClicked -> retry()
            is ActivityIntent.ConflictCompareClicked -> Unit
            is ActivityIntent.ConflictKeepMineClicked -> Unit
            is ActivityIntent.UndoClicked -> Unit
            ActivityIntent.FullHistoryClicked -> Unit
        }
    }

    private fun retry() {
        viewModelScope.launch {
            runCatching { sourcesController.runSync() }
                .exceptionOrNull()
                ?.let { reporter.report(it, "Retry pass failed") }
        }
    }

    private fun FileTransfer.toUi(): TransferUi {
        val fileName = path.substringAfterLast('/')

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
