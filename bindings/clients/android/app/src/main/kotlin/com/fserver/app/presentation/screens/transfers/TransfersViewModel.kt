package com.fserver.app.presentation.screens.transfers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.composable.model.TransferUi
import com.fserver.app.presentation.screens.transfers.model.TransfersIntent
import com.fserver.app.presentation.screens.transfers.model.TransfersState
import com.fserver.common.model.FileSize
import com.fserver.core.sync.SourcesController
import com.fserver.core.sync.progress.FileTransfer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

class TransfersViewModel(
    private val sourcesController: SourcesController,
) : ViewModel() {

    val state: StateFlow<TransfersState> = sourcesController.progress.transfers
        .map { transfers -> TransfersState(transfers = transfers.map { it.toUi() }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransfersState())

    fun onIntent(intent: TransfersIntent) {
        when (intent) {
            TransfersIntent.RetryClicked -> retry()
            TransfersIntent.ClearClicked -> sourcesController.progress.clearFinished()
        }
    }

    private fun retry() {
        viewModelScope.launch {
            runCatching { sourcesController.runSync() }
                .exceptionOrNull()
                ?.let { Timber.w(it, "Retry pass failed") }
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
}
