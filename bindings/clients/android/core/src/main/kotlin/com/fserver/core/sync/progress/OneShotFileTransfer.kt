package com.fserver.core.sync.progress

import kotlin.time.Instant

/** One file of a one-shot transfer on the move - the one-shot counterpart of [FileTransfer]. */
data class OneShotFileTransfer(
    val transferId: String,
    /** The file's position in the transfer. */
    val index: Int,
    val direction: FileTransfer.Direction,
    val name: String,
    val totalBytes: Long,
    val transferredBytes: Long,
    val state: FileTransfer.State,
    val startedAt: Instant,
    val updatedAt: Instant,
) {
    /** `null` while the size is unknown, so the UI draws an indeterminate bar rather than 0 %. */
    val progress: Float?
        get() = if (totalBytes <= 0L) null
        else (transferredBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)

    val isFinished: Boolean get() = state == FileTransfer.State.Completed || state is FileTransfer.State.Failed
}
