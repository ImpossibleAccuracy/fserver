package com.fserver.app.presentation.screens.settings.transfers.model

import com.fserver.core.files.SourceLocation

sealed interface TransfersIntent {
    data class DestinationPicked(val destination: SourceLocation.Hostable) : TransfersIntent
    data class AutoAcceptChanged(val enabled: Boolean) : TransfersIntent
    data class CancelClicked(val transferId: String) : TransfersIntent
    data class RetryClicked(val transferId: String) : TransfersIntent
    data class DeleteClicked(val transferId: String) : TransfersIntent
    data object ClearFinishedClicked : TransfersIntent
}
