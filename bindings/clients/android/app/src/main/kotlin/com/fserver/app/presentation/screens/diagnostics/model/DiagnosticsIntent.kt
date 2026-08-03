package com.fserver.app.presentation.screens.diagnostics.model

sealed interface DiagnosticsIntent {
    data object RecheckClicked : DiagnosticsIntent
}