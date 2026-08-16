package com.fserver.app.presentation.screens.discovery.qr.model

import androidx.compose.runtime.Immutable
import com.fserver.core.domain.model.network.PeerLocator

@Immutable
data class QrScanState(
    val isConnecting: Boolean = false,
    val error: Error? = null,
    /** Set once the scanned address answered; the screen navigates on and clears it. */
    val found: PeerLocator? = null,
) {
    sealed interface Error {
        data object MalformedCode : Error
        data object Unreachable : Error
        data class Unknown(val message: String?) : Error
    }
}
