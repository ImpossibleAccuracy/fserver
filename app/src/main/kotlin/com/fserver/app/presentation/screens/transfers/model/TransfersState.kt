package com.fserver.app.presentation.screens.transfers.model

import com.fserver.app.presentation.model.TransferUi

data class TransfersState(
    val transfers: List<TransferUi> = emptyList(),
)