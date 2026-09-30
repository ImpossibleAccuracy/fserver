package com.fserver.core.oneshot.model

import com.fserver.core.files.SourceLocation
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.time.Instant

class OneShotTransferTest {

    @Test
    fun `incoming transfer cannot run without a destination`() {
        for (status in listOf(OneShotTransfer.Status.Active, OneShotTransfer.Status.Completed)) {
            assertThrows(IllegalArgumentException::class.java) {
                transfer(OneShotTransfer.Direction.Incoming(null), status)
            }
        }

        // Unanswered, refused or broken before an answer: nowhere to write is expected.
        for (status in listOf(OneShotTransfer.Status.Pending, OneShotTransfer.Status.Declined, OneShotTransfer.Status.Cancelled)) {
            transfer(OneShotTransfer.Direction.Incoming(null), status)
        }
        transfer(OneShotTransfer.Direction.Incoming(SourceLocation.Internal("inbox")), OneShotTransfer.Status.Active)
        transfer(OneShotTransfer.Direction.Outgoing(SourceLocation.Media), OneShotTransfer.Status.Completed)
    }

    @Test
    fun `file indices are unique`() {
        assertThrows(IllegalArgumentException::class.java) {
            transfer(OneShotTransfer.Direction.Outgoing(SourceLocation.Media), OneShotTransfer.Status.Pending, files = listOf(file(0), file(0)))
        }
    }

    @Test
    fun `progress stays within the file`() {
        assertThrows(IllegalArgumentException::class.java) { file(0, committed = 11) }
        assertThrows(IllegalArgumentException::class.java) { file(0, committed = -1) }
    }

    private fun transfer(
        direction: OneShotTransfer.Direction,
        status: OneShotTransfer.Status,
        files: List<OneShotTransferFile> = listOf(file(0)),
    ) = OneShotTransfer(
        id = "t",
        peer = OneShotTransfer.Peer("device-peer", "Pixel"),
        direction = direction,
        status = status,
        files = files,
        createdAt = Instant.fromEpochMilliseconds(0),
    )

    private fun file(index: Int, committed: Long = 0) = OneShotTransferFile(
        index = index,
        name = "a.jpg",
        size = 10,
        locator = null,
        committedBytes = committed,
    )
}
