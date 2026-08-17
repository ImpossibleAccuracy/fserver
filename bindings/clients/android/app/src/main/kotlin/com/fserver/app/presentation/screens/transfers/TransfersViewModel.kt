package com.fserver.app.presentation.screens.transfers

import androidx.lifecycle.ViewModel
import com.fserver.app.data.DemoContentSource
import com.fserver.app.presentation.composable.shared.TransferUi
import com.fserver.app.presentation.screens.transfers.model.TransfersIntent
import com.fserver.app.presentation.screens.transfers.model.TransfersState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The transfer queue.
 *
 * Pausing and resuming keep the transfer's position rather than restarting it, and a
 * dropped connection is modelled exactly like a user pause — same resumable position,
 * different cause. That is why "clear" only removes finished rows: nothing that could
 * still be resumed is ever thrown away by a list-tidying action.
 */
class TransfersViewModel(
    content: DemoContentSource,
) : ViewModel() {

    private val _state = MutableStateFlow(TransfersState(transfers = content.transfers()))
    val state: StateFlow<TransfersState> = _state.asStateFlow()

    fun onIntent(intent: TransfersIntent) {
        when (intent) {
            is TransfersIntent.PauseClicked -> updateTransfer(intent.transferId, ::pause)
            is TransfersIntent.ResumeClicked -> updateTransfer(intent.transferId, ::resume)

            TransfersIntent.ClearClicked -> _state.value = _state.value.copy(
                transfers = _state.value.transfers.filterNot { it is TransferUi.Completed },
            )
        }
    }

    private fun updateTransfer(id: String, transform: (TransferUi) -> TransferUi) {
        _state.value = _state.value.copy(
            transfers = _state.value.transfers.map { transfer ->
                if (transfer.id == id) transform(transfer) else transfer
            },
        )
    }

    private fun pause(transfer: TransferUi): TransferUi = when (transfer) {
        is TransferUi.Running -> TransferUi.Paused(
            id = transfer.id,
            fileName = transfer.fileName,
            progress = transfer.progress,
            transferredLabel = transfer.transferredLabel,
            totalLabel = transfer.totalLabel,
        )

        else -> transfer
    }

    private fun resume(transfer: TransferUi): TransferUi = when (transfer) {
        is TransferUi.Paused -> TransferUi.Running(
            id = transfer.id,
            fileName = transfer.fileName,
            progress = transfer.progress,
            transferredLabel = transfer.transferredLabel,
            totalLabel = transfer.totalLabel,
            speedLabel = RESUMED_SPEED_PLACEHOLDER,
            etaLabel = RESUMED_ETA_PLACEHOLDER,
        )

        // Resuming after a drop re-opens the connection first; the queue is what tells the
        // user it is moving again, so it goes straight back to the running row.
        is TransferUi.Interrupted -> TransferUi.Running(
            id = transfer.id,
            fileName = transfer.fileName,
            progress = transfer.stoppedAtPercent / 100f,
            transferredLabel = "—",
            totalLabel = "—",
            speedLabel = RESUMED_SPEED_PLACEHOLDER,
            etaLabel = RESUMED_ETA_PLACEHOLDER,
        )

        else -> transfer
    }

    private companion object {
        // Real figures arrive with the first progress report from :core.
        const val RESUMED_SPEED_PLACEHOLDER = "—"
        const val RESUMED_ETA_PLACEHOLDER = "—"
    }
}
