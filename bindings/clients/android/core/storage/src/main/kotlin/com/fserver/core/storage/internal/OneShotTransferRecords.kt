package com.fserver.core.storage.internal

import com.fserver.core.files.SourceLocation
import com.fserver.core.oneshot.model.OneShotTransfer
import com.fserver.core.oneshot.model.OneShotTransferFile
import kotlin.time.Instant
import com.fserver.core.storage.database.OneShotTransfer as DBOneShotTransfer
import com.fserver.core.storage.database.OneShotTransferFile as DBOneShotTransferFile

/**
 * Stored names of transfer enums, both directions together - see [SourceRecords] for why.
 * Status literals are also spelled in `OneShotTransfer.sq`, which filters on them.
 */
internal object OneShotTransferRecords {
    private const val Outgoing = "Outgoing"
    private const val Incoming = "Incoming"

    private const val Pending = "Pending"
    private const val Active = "Active"
    private const val Completed = "Completed"
    private const val Declined = "Declined"
    private const val Cancelled = "Cancelled"
    private const val Failed = "Failed"

    fun discriminatorOf(direction: OneShotTransfer.Direction): String = when (direction) {
        OneShotTransfer.Direction.Outgoing -> Outgoing
        is OneShotTransfer.Direction.Incoming -> Incoming
    }

    fun discriminatorOf(status: OneShotTransfer.Status): String = when (status) {
        OneShotTransfer.Status.Pending -> Pending
        OneShotTransfer.Status.Active -> Active
        OneShotTransfer.Status.Completed -> Completed
        OneShotTransfer.Status.Declined -> Declined
        OneShotTransfer.Status.Cancelled -> Cancelled
        is OneShotTransfer.Status.Failed -> Failed
    }

    fun discriminatorOf(status: OneShotTransferFile.Status): String = when (status) {
        OneShotTransferFile.Status.Pending -> Pending
        OneShotTransferFile.Status.Completed -> Completed
        is OneShotTransferFile.Status.Failed -> Failed
    }

    /** Throws when the rows do not rebuild a transfer; the caller skips it. */
    fun transferOf(
        row: DBOneShotTransfer,
        files: List<DBOneShotTransferFile>,
        attributes: SourceRecords.Reader,
    ): OneShotTransfer = OneShotTransfer(
        id = row.id,
        peer = OneShotTransfer.Peer(deviceId = row.peerDeviceId, displayName = row.peerName),
        direction = when (row.direction) {
            Outgoing -> OneShotTransfer.Direction.Outgoing
            Incoming -> OneShotTransfer.Direction.Incoming(
                destination = row.destination?.let { destinationOf(row.id, it, attributes) },
            )

            else -> error("unknown direction '${row.direction}'")
        },
        status = when (row.status) {
            Pending -> OneShotTransfer.Status.Pending
            Active -> OneShotTransfer.Status.Active
            Completed -> OneShotTransfer.Status.Completed
            Declined -> OneShotTransfer.Status.Declined
            Cancelled -> OneShotTransfer.Status.Cancelled
            Failed -> OneShotTransfer.Status.Failed(requireNotNull(row.failureReason) { "failed without reason" })
            else -> error("unknown status '${row.status}'")
        },
        files = files.map(::fileOf),
        createdAt = Instant.fromEpochMilliseconds(row.createdAtEpochMs),
        finishedAt = row.finishedAtEpochMs?.let(Instant::fromEpochMilliseconds),
    )

    private fun destinationOf(
        id: String,
        discriminator: String,
        attributes: SourceRecords.Reader,
    ): SourceLocation.Hostable {
        val location = SourceRecords.locationOf(id, discriminator, attributes)

        return location as? SourceLocation.Hostable
            ?: error("destination '$discriminator' is unreadable or not hostable")
    }

    private fun fileOf(row: DBOneShotTransferFile) = OneShotTransferFile(
        index = row.position.toInt(),
        name = row.name,
        size = row.size,
        locator = row.locator,
        committedBytes = row.committedBytes,
        status = when (row.status) {
            Pending -> OneShotTransferFile.Status.Pending
            Completed -> OneShotTransferFile.Status.Completed
            Failed -> OneShotTransferFile.Status.Failed(requireNotNull(row.failureReason) { "failed without reason" })
            else -> error("unknown file status '${row.status}'")
        },
    )
}
