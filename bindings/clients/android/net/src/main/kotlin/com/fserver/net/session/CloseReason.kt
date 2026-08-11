package com.fserver.net.session

sealed interface CloseReason {
    data object Normal : CloseReason
    data object Idle : CloseReason
    data object RejectedByUser : CloseReason

    /** The peer closed, with whatever it said about why. */
    data class Remote(val detail: String) : CloseReason

    data class LinkLost(val cause: Throwable?) : CloseReason
    data class Protocol(val detail: String) : CloseReason
    data class Local(val detail: String) : CloseReason
}
