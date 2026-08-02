package com.fserver.app.presentation.screens.transfers.model

sealed interface TransfersIntent {
    data class PauseClicked(val transferId: String) : TransfersIntent
    data class ResumeClicked(val transferId: String) : TransfersIntent
    data object ClearClicked : TransfersIntent
}