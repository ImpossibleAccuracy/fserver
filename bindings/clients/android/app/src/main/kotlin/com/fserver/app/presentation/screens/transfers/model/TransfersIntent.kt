package com.fserver.app.presentation.screens.transfers.model

sealed interface TransfersIntent {
    data object RetryClicked : TransfersIntent
    data object ClearClicked : TransfersIntent
}
