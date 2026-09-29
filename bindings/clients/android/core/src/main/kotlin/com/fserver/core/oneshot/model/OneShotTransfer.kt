package com.fserver.core.oneshot.model

import com.fserver.core.files.SourceLocation
import kotlin.time.Instant

/**
 * One-shot exchange of files with a peer, outside any source: no index, versions or tombstones.
 * Both sides hold it under the same [id], chosen by the sender.
 */
data class OneShotTransfer(
    val id: String,
    val peer: Peer,
    val direction: Direction,
    val status: Status,
    /** In the order the sender listed them. */
    val files: List<OneShotTransferFile>,
    val createdAt: Instant,
    /** Set once [status] is [Status.isFinished]. */
    val finishedAt: Instant? = null,
) {
    init {
        require(files.map { it.index }.toSet().size == files.size) { "Duplicate file index in $id" }

        val incoming = direction as? Direction.Incoming
        require(incoming == null || incoming.destination != null || !status.needsDestination) {
            "Incoming transfer $id is $status without a destination"
        }
    }

    /**
     * Who is on the other end. [displayName] is a snapshot taken with the ask: an unpaired peer has
     * no trust record to read a name from later.
     */
    data class Peer(val deviceId: String, val displayName: String)

    sealed interface Direction {
        data object Outgoing : Direction

        /** [destination] is null until this device's user accepts. */
        data class Incoming(val destination: SourceLocation.Hostable?) : Direction
    }

    sealed interface Status {
        /** Waiting on the receiving side's user. */
        data object Pending : Status

        /** Accepted. Bytes are flowing, or waiting for the peer to come back. */
        data object Active : Status

        data object Completed : Status

        data object Declined : Status

        /** Stopped by either side's user. */
        data object Cancelled : Status

        data class Failed(val reason: String) : Status

        /** Final: nothing moves this transfer anymore. */
        val isFinished: Boolean
            get() = this != Pending && this != Active

        /** Receiving side has somewhere to write, or has written. */
        val needsDestination: Boolean
            get() = this == Active || this == Completed
    }
}
