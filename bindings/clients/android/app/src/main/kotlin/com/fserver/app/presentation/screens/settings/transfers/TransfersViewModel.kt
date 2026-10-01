package com.fserver.app.presentation.screens.settings.transfers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.domain.documents.isOwnDocument
import com.fserver.app.domain.oneshot.OneShotRepository
import com.fserver.app.presentation.screens.settings.transfers.model.TransfersIntent
import com.fserver.app.presentation.screens.settings.transfers.model.TransfersState
import com.fserver.app.presentation.shared.error.AppError
import com.fserver.app.presentation.shared.error.ErrorReporter
import com.fserver.app.util.stateInScreen
import com.fserver.common.model.FileSize
import com.fserver.core.files.SourceLocation
import com.fserver.core.oneshot.OneShotTransfersController
import com.fserver.core.oneshot.model.OneShotTransfer
import com.fserver.core.oneshot.model.OneShotTransferFile
import com.fserver.core.storage.OneShotTransfersRepository
import com.fserver.core.sync.progress.OneShotFileTransfer
import com.fserver.core.sync.progress.SyncProgressRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** One-shot transfer preferences, which only `:app` keeps, and the history `:core:storage` keeps. */
class TransfersViewModel(
    private val settings: OneShotRepository,
    private val repository: OneShotTransfersRepository,
    private val controller: OneShotTransfersController,
    progress: SyncProgressRepository,
    private val reporter: ErrorReporter,
) : ViewModel() {

    val state: StateFlow<TransfersState> = combine(
        settings.destination,
        settings.autoAccept,
        repository.transfers,
        progress.oneShotTransfers,
    ) { destination, autoAccept, transfers, live ->
        val liveByFile = live.associateBy { it.transferId to it.index }

        TransfersState(
            destination = destination,
            autoAccept = autoAccept,
            transfers = transfers.map { it.toUi(liveByFile) },
        )
    }.stateInScreen(viewModelScope, TransfersState())

    fun onIntent(intent: TransfersIntent) {
        when (intent) {
            is TransfersIntent.DestinationPicked -> recordDestination(intent.destination)
            is TransfersIntent.AutoAcceptChanged -> viewModelScope.launch { settings.setAutoAccept(intent.enabled) }
            is TransfersIntent.CancelClicked -> run("cancel") { controller.cancel(intent.transferId) }
            is TransfersIntent.RetryClicked -> run("retry") { controller.retry(intent.transferId) }
            is TransfersIntent.DeleteClicked -> viewModelScope.launch { repository.delete(intent.transferId) }
            TransfersIntent.ClearFinishedClicked -> viewModelScope.launch { repository.clearFinished() }
        }
    }

    fun reportError(error: Throwable, context: String) = reporter.report(error, context)

    private fun recordDestination(destination: SourceLocation.Hostable) {
        if (destination.isOwnDocument) return reporter.report(AppError.OwnFolder)
        viewModelScope.launch { settings.setDestination(destination) }
    }

    private fun run(what: String, action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            action().onFailure { reporter.report(it, "could not $what transfer") }
        }
    }
}

private fun OneShotTransfer.toUi(live: Map<Pair<String, Int>, OneShotFileTransfer>): TransfersState.TransferUi {
    val incoming = direction is OneShotTransfer.Direction.Incoming
    val total = files.sumOf { it.size }
    val done = files.sumOf { file ->
        if (file.status == OneShotTransferFile.Status.Completed) file.size
        else live[id to file.index]?.transferredBytes ?: 0L
    }

    return TransfersState.TransferUi(
        id = id,
        peerName = peer.displayName,
        outgoing = !incoming,
        status = when (val status = status) {
            OneShotTransfer.Status.Pending -> TransfersState.StatusUi.Pending
            OneShotTransfer.Status.Active -> TransfersState.StatusUi.Active
            OneShotTransfer.Status.Completed -> TransfersState.StatusUi.Completed
            OneShotTransfer.Status.Declined -> TransfersState.StatusUi.Declined
            OneShotTransfer.Status.Cancelled -> TransfersState.StatusUi.Cancelled
            is OneShotTransfer.Status.Failed -> TransfersState.StatusUi.Failed(status.reason)
        },
        files = files.map { file ->
            TransfersState.FileUi(
                index = file.index,
                name = file.name,
                size = FileSize(file.size),
                // Only a whole received file: a partial one is not worth handing to a viewer.
                openLocator = file.locator.takeIf { incoming && file.status == OneShotTransferFile.Status.Completed },
            )
        },
        totalSize = FileSize(total),
        progress = if (status == OneShotTransfer.Status.Active && total > 0) done.toFloat() / total else null,
        createdAt = createdAt,
    )
}
