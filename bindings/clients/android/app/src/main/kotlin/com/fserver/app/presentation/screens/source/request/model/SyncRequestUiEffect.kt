package com.fserver.app.presentation.screens.source.request.model

sealed interface SyncRequestUiEffect {
    data object Declined : SyncRequestUiEffect

    data class Accepted(val sourceId: String) : SyncRequestUiEffect
}
