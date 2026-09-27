package com.fserver.app.presentation.screens.source.request.shared.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.core.network.device.model.DeviceKind

/** A peer's ask to host one of its sources here, as the screens answering it read it. */
@Immutable
data class SyncRequestUi(
    /** Chosen by the peer. Both halves of the source answer to it. */
    val sourceId: String,
    /** Display name of the device that asked, or its id when nothing better is known. */
    val deviceName: String,
    /** What the asking side called the source. */
    val label: String,
    /** The mode the asking side registered, in the send flow's own vocabulary. */
    val mode: SourceModeUi,
    val deviceKind: DeviceKind? = null,
    /** Leading groups of the asking device's key fingerprint; null when its key is not on record. */
    val fingerprint: String? = null,
    /** How much the peer is about to send; null while the ask does not say. */
    val files: Int? = null,
    val bytes: Long? = null,
)
