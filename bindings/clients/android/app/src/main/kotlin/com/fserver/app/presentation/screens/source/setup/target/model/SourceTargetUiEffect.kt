package com.fserver.app.presentation.screens.source.setup.target.model

import com.fserver.core.network.info.model.PeerLocator

sealed interface SourceTargetUiEffect {
    data class NavigatePairing(val peer: PeerLocator) : SourceTargetUiEffect

    data object RouteUnknown : SourceTargetUiEffect

    data class ReconnectFailed(val reason: String?) : SourceTargetUiEffect
}
