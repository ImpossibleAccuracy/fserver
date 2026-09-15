package com.fserver.app.presentation.screens.files.source.model

sealed interface SourceActionsUiEffect {
    data object Dismiss : SourceActionsUiEffect
}
