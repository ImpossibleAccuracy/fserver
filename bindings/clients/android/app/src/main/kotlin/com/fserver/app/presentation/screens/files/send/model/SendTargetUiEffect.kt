package com.fserver.app.presentation.screens.files.send.model

sealed interface SendTargetUiEffect {
    data object NavigateFinished : SendTargetUiEffect

    data class ShowMessage(val message: String) : SendTargetUiEffect
}
