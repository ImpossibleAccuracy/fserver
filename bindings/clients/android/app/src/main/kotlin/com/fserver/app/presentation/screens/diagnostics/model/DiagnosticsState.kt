package com.fserver.app.presentation.screens.diagnostics.model

import com.fserver.app.presentation.composable.shared.DiagnosticCheckUi

data class DiagnosticsState(
    val checks: List<DiagnosticCheckUi> = emptyList(),
    val running: Boolean = false,
)