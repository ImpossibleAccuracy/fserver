package com.fserver.app.presentation.screens.files.source.model

sealed interface SourceActionsIntent {
    data class NameChanged(val name: String) : SourceActionsIntent
    data object SaveClicked : SourceActionsIntent
    data object RemoveClicked : SourceActionsIntent
    data object RemoveCancelled : SourceActionsIntent
    data object RemoveConfirmed : SourceActionsIntent
}
