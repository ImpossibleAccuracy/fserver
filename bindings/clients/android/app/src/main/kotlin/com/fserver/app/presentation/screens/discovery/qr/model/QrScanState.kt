package com.fserver.app.presentation.screens.discovery.qr.model

import androidx.compose.runtime.Immutable

@Immutable
data class QrScanState(
    val isConnecting: Boolean = false,
    val error: Error? = null,
    val foundDeviceId: String? = null,
) {
    sealed interface Error {
        data object MalformedCode : Error
        data object Unreachable : Error
        data class Unknown(val message: String?) : Error
    }
}
