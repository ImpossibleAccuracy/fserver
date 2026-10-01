package com.fserver.app.presentation.share.model

sealed interface ShareUiEffect {
    /** Copied and offered: the share sheet's grant is no longer needed. */
    data class Offered(val count: Int, val deviceName: String) : ShareUiEffect
}
