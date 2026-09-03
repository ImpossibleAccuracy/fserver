package com.fserver.app.presentation.screens.request.location.model

sealed interface SyncRequestLocationUiEffect {
    data object NavigateToDone : SyncRequestLocationUiEffect

    data class ShowMessage(val message: String) : SyncRequestLocationUiEffect
}
