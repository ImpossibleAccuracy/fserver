package com.fserver.app.presentation.screens.source.request.location.model

sealed interface SyncRequestLocationUiEffect {
    data object NavigateToProgress : SyncRequestLocationUiEffect
}
