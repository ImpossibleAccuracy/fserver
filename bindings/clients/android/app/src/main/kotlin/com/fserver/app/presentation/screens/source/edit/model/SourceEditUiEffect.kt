package com.fserver.app.presentation.screens.source.edit.model

sealed interface SourceEditUiEffect {
    data object NavigateBack : SourceEditUiEffect
}
