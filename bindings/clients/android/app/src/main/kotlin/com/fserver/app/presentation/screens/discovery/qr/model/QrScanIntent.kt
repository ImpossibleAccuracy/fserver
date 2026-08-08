package com.fserver.app.presentation.screens.discovery.qr.model

sealed interface QrScanIntent {
    /** The decoder read something. Whether it is a connection code is not decided here. */
    data class CodeScanned(val payload: String) : QrScanIntent

    /** The screen has navigated on; drop the result so returning here does not re-fire it. */
    data object ResultConsumed : QrScanIntent
}
