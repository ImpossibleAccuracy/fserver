package com.fserver.app.presentation.screens.source.setup.conditions.model

sealed interface SourceConditionsUiEffect {
    /** The source is registered — what is left is the peer's answer and the first pass. */
    data class NavigateToProgress(val sourceId: String) : SourceConditionsUiEffect
}
