package com.fserver.core.sync.progress

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

data class FileTransfer(
    val key: FileTransferKey,
    val path: String,
    val totalBytes: Long,
    val transferredBytes: Long,
    val state: State,
    val startedAt: Instant,
    val updatedAt: Instant,
) {
    /** `null` while the size is unknown, so the UI draws an indeterminate bar rather than 0 %. */
    val progress: Float?
        get() = if (totalBytes <= 0L) null
        else (transferredBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)

    /** Averaged over the whole transfer, so it settles rather than jumping per chunk. */
    val bytesPerSecond: Long?
        get() {
            if (state != State.Running || transferredBytes <= 0L) return null

            val elapsedMillis = (updatedAt - startedAt).inWholeMilliseconds
            return if (elapsedMillis <= 0L) null else transferredBytes * 1000L / elapsedMillis
        }

    val eta: Duration?
        get() {
            val speed = bytesPerSecond?.takeIf { it > 0L } ?: return null
            return ((totalBytes - transferredBytes).coerceAtLeast(0L) / speed).seconds
        }

    val isFinished: Boolean get() = state == State.Completed || state is State.Failed

    /** Which way the bytes go, as this device sees them. */
    enum class Direction {
        /** This device is sending: a planned upload, or a file a peer asked us to hand back. */
        Outgoing,

        /** This device is receiving: a peer pushing to us, or a download we asked for. */
        Incoming,
    }

    sealed interface State {
        /** Planned this pass, nothing sent yet. */
        data object Queued : State

        data object Running : State

        /** Across and hash-checked. */
        data object Completed : State

        /** Stopped short. The next pass re-plans it from wherever the index got to. */
        data class Failed(val reason: String?) : State
    }
}
