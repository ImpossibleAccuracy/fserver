package com.fserver.app.presentation.screens.qr.model

data class QrScanState(
    /** No camera is bound yet; the viewfinder is a placeholder. */
    val cameraActive: Boolean = false,
)