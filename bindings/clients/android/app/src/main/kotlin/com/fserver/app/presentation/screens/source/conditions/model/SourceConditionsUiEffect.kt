package com.fserver.app.presentation.screens.source.conditions.model

sealed interface SourceConditionsUiEffect {
    data object NavigateToDone : SourceConditionsUiEffect
}
