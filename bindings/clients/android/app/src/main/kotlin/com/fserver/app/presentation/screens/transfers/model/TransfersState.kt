package com.fserver.app.presentation.screens.transfers.model

import com.fserver.app.presentation.composable.model.TransferUi

data class TransfersState(
    val transfers: List<TransferUi> = emptyList(),
)