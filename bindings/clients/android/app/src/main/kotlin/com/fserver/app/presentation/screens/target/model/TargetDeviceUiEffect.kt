package com.fserver.app.presentation.screens.target.model

sealed interface TargetDeviceUiEffect {
    data object NavigateFinished : TargetDeviceUiEffect

    /** Source flow: the target has a name now, so the mode can ask for its conditions. */
    data object NavigateToConditions : TargetDeviceUiEffect

    data class ShowMessage(val message: String) : TargetDeviceUiEffect
}
