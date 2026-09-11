package com.fserver.app.presentation.screens.source.setup.mode.model

import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi

sealed interface SourceModeIntent {
    data class ModeSelected(val mode: SourceModeUi) : SourceModeIntent

    data object Confirmed : SourceModeIntent
}
