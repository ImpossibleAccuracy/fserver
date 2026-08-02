package com.fserver.app.presentation.screens.pairing.model

import com.fserver.app.presentation.model.PairingCandidateUi

data class PairingState(
    val candidate: PairingCandidateUi,
    val rememberDevice: Boolean = true,
)