package com.fserver.app.presentation.screens.source.details.model

sealed interface SourceDetailsUiEffect {
    data object NavigateBack : SourceDetailsUiEffect
}
