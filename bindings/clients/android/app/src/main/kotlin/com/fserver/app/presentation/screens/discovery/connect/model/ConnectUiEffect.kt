package com.fserver.app.presentation.screens.discovery.connect.model

import com.fserver.core.network.info.model.PeerLocator

sealed interface ConnectUiEffect {
    /** The answer this screen exists to give: a device that is connected right now. */
    data class DeviceSelected(val deviceId: String) : ConnectUiEffect

    /** Nothing is selected until the handshake is done, so the pairing screen gets it first. */
    data class NavigatePairing(val peer: PeerLocator) : ConnectUiEffect

    data class ReconnectFailed(val reason: String?) : ConnectUiEffect
}
