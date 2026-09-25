package com.fserver.app.presentation.screens.device.details.model

import com.fserver.core.network.info.model.PeerLocator

sealed interface DeviceDetailsUiEffect {
    /** Forgetting empties the screen, so it leaves rather than sitting there with nothing to say. */
    data object NavigateBack : DeviceDetailsUiEffect

    data class NavigatePairing(val peer: PeerLocator) : DeviceDetailsUiEffect
}
