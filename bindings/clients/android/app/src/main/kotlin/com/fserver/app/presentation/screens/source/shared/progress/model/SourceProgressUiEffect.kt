package com.fserver.app.presentation.screens.source.shared.progress.model

sealed interface SourceProgressUiEffect {
    data object NavigateToDone : SourceProgressUiEffect
}
