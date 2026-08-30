package com.fserver.app.presentation.screens.source.mode.model

import com.fserver.app.presentation.screens.source.shared.composable.SourceModeUi

sealed interface SourceModeIntent {
    data class ModeSelected(val mode: SourceModeUi) : SourceModeIntent

    /** Partial grants only: reopens the system picker to widen what the app can see. */
    data object ChangeSelectionClicked : SourceModeIntent
}
