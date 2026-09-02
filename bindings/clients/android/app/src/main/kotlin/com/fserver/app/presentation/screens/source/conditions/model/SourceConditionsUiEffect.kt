package com.fserver.app.presentation.screens.source.conditions.model

sealed interface SourceConditionsUiEffect {
    /** The source is registered — what is left is the peer's answer and the first pass. */
    data class NavigateToUpload(val sourceId: String) : SourceConditionsUiEffect
}
