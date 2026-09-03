package com.fserver.app.presentation.screens.source.upload.model

import androidx.compose.runtime.Immutable

/**
 * The wait between "turned on" and "running": the peer has to take on its half of the source
 * before a single byte moves, and only it can decide that.
 *
 * Neither phase is something the user drives, so the screen offers one control — leaving. The
 * source is registered either way, so leaving cancels nothing.
 */
@Immutable
data class SourceUploadState(
    val phase: Phase = Phase.WaitingForPeer,
    val targetName: String = "",
    val sourceLabel: String = "",
    val files: Int = 0,
    val progress: Float = 0f,
    val progressDetail: String = "",
    /** Why the peer refused, when it did. */
    val reason: String? = null,
) {
    enum class Phase {
        /** Registered here, waiting on the other device's user. */
        WaitingForPeer,

        /** Both devices hold the source. The first pass is running. */
        Syncing,

        /** The peer said no. Nothing will be sent, and the record says why. */
        Refused,
    }
}
