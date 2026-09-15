package com.fserver.app.presentation.screens.discovery.qr.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.error.AppError
import com.fserver.core.network.info.model.PeerLocator

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
        /** Anything the parser named; the line it carries is what the viewfinder shows. */
        data class Failed(val error: AppError) : Error
    }
}
