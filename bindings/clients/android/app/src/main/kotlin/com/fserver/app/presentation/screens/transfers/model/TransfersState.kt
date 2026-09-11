package com.fserver.app.presentation.screens.transfers.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.composable.model.TransferUi

@Immutable
data class TransfersState(
    val transfers: List<TransferUi> = emptyList(),
) {
    val isEmpty: Boolean get() = transfers.isEmpty()
}
