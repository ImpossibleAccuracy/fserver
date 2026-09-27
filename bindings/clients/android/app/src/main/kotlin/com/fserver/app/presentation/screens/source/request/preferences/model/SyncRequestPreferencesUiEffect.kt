package com.fserver.app.presentation.screens.source.request.preferences.model

sealed interface SyncRequestPreferencesUiEffect {
    data object NavigateToProgress : SyncRequestPreferencesUiEffect
}
