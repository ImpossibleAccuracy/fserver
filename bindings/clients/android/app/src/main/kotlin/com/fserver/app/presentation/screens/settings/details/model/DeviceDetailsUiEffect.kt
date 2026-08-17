package com.fserver.app.presentation.screens.settings.details.model

sealed interface DeviceDetailsUiEffect {
    /** Forgetting empties the screen, so it leaves rather than sitting there with nothing to say. */
    data object NavigateBack : DeviceDetailsUiEffect
}
