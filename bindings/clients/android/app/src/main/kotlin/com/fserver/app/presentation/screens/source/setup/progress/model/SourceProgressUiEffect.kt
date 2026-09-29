package com.fserver.app.presentation.screens.source.setup.progress.model

sealed interface SourceProgressUiEffect {
    data object NavigateToDone : SourceProgressUiEffect
}
