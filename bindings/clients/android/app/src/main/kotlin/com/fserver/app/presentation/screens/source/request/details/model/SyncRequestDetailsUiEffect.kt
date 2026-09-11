package com.fserver.app.presentation.screens.source.request.details.model

sealed interface SyncRequestDetailsUiEffect {
    data object NavigateBack : SyncRequestDetailsUiEffect

    data class ShowMessage(val message: String) : SyncRequestDetailsUiEffect
}
