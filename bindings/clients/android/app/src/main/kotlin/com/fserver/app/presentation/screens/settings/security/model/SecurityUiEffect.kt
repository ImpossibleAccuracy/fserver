package com.fserver.app.presentation.screens.settings.security.model

sealed interface SecurityUiEffect {
    data object NavigateToPinSetup : SecurityUiEffect
}
