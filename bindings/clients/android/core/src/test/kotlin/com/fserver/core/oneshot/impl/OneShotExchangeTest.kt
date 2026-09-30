package com.fserver.core.oneshot.impl

import com.fserver.core.files.SourceLocation
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.dto.OneShotFileDto
import com.fserver.core.oneshot.model.OneShotTransfer
import com.fserver.core.support.FakePeerSession
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.peerIdentity
import com.fserver.core.sync.remote.PeerConnector
import com.fserver.files.FilesNode
import com.fserver.common.model.FileSize
import com.fserver.common.task.progressTask
import com.fserver.core.sync.server.handler.upload.oneshot.OneShotStaging
import com.fserver.files.fs.FileSystem
import com.fserver.files.fs.FileSystemSource
import com.fserver.files.fs.scan.FoundFile
import io.mockk.every
import kotlin.time.Instant
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Setting a one-shot transfer up and ending it. Every message names an id the peer chose, so each
 * is checked against who the transfer was recorded with.
 */
class OneShotExchangeTest {

    private val clock = MutableTimeProvider()
    private val storage = FakeStorage(clock = clock)
    private val peers = mockk<PeerConnector>()
    private val node = mockk<FilesNode>()
    private val sender = mockk<OneShotSender>(relaxed = true)
    private val staging = mockk<OneShotStaging>(relaxed = true)
    private val background = TestScope()

    private val ownerSession = FakePeerSession(identity = peerIdentity(OwnerId))

    private val exchange = OneShotExchange(
        storage = storage,
        node = node,
        peers = peers,
        sender = sender,
        staging = staging,
        timeProvider = clock,
        backgroundScope = background,
    )

    @Before
    fun setUp() {
        coEvery { peers.connectToDevice(any<String>()) } returns ownerSession
        // What a scan of the shared uris reports: the provider's names and sizes.
        every { node.openSource(any()) } answers {
            val shared = firstArg<FileSystemSource.Shared>()
            mockk<FileSystem> {
                every { scan() } returns progressTask {
                    shared.uris.map { FoundFile(path = "photo.jpg", locator = it, size = FileSize(10), lastModified = Instant.DISTANT_PAST) }
                }
            }
        }
    }

    @Test
    fun `a new transfer is recorded and offered with what the files are`() = runTest {
        val transfer = exchange.create(OwnerId, listOf("content://a/1", "content://a/2"))
        background.advanceUntilIdle()

        assertEquals(OneShotTransfer.Status.Pending, storage.oneShotTransfers.find(transfer.id)?.status)
        val offer = ownerSession.sent.single() as FileServerMessages.OneShot.Offer
        assertEquals(transfer.id, offer.transferId)
        assertEquals(listOf(0, 1), offer.files.map { it.index })
        assertEquals("photo.jpg", offer.files.first().name)
    }

    @Test
    fun `an offer is parked for the user, not answered`() = runTest {
        exchange.onOffer(peerIdentity(OwnerId), offer())

        val parked = storage.oneShotTransfers.find(TransferId)!!
        assertEquals(OneShotTransfer.Status.Pending, parked.status)
        assertEquals(OneShotTransfer.Direction.Incoming(null), parked.direction)
        assertEquals("Owner", parked.peer.displayName)
        assertTrue(ownerSession.sent.isEmpty())
    }

    @Test
    fun `a malformed offer is dropped whole`() = runTest {
        val malformed = listOf(
            offer(files = emptyList()),
            offer(files = listOf(dto(0), dto(0))),
            offer(files = listOf(dto(1))),
            offer(files = listOf(dto(0, size = -1))),
            offer(files = listOf(dto(0, name = "x".repeat(5000)))),
        )

        for (message in malformed) {
            exchange.onOffer(peerIdentity(OwnerId), message)
            assertNull(message.files.toString(), storage.oneShotTransfers.find(TransferId))
        }
    }

    @Test
    fun `another device cannot take over a transfer by reusing its id`() = runTest {
        exchange.onOffer(peerIdentity(OwnerId), offer())

        exchange.onOffer(peerIdentity(StrangerId), offer(files = listOf(dto(0, name = "evil.apk"))))

        val kept = storage.oneShotTransfers.find(TransferId)!!
        assertEquals(OwnerId, kept.peer.deviceId)
        assertEquals("photo.jpg", kept.files.single().name)
    }

    @Test
    fun `a re-offer of something accepted is answered again`() = runTest {
        exchange.onOffer(peerIdentity(OwnerId), offer())
        exchange.accept(TransferId, Destination)
        ownerSession.sent.clear()

        exchange.onOffer(peerIdentity(OwnerId), offer())

        assertEquals(true, (ownerSession.sent.single() as FileServerMessages.OneShot.Decision).accepted)
    }

    @Test
    fun `accepting records where to write and tells the sender`() = runTest {
        exchange.onOffer(peerIdentity(OwnerId), offer())

        exchange.accept(TransferId, Destination)

        val accepted = storage.oneShotTransfers.find(TransferId)!!
        assertEquals(OneShotTransfer.Status.Active, accepted.status)
        assertEquals(OneShotTransfer.Direction.Incoming(Destination), accepted.direction)
        assertEquals(true, (ownerSession.sent.single() as FileServerMessages.OneShot.Decision).accepted)

        assertThrows(IllegalStateException::class.java) { kotlinx.coroutines.runBlocking { exchange.accept(TransferId, Destination) } }
    }

    @Test
    fun `declining is final and told to the sender`() = runTest {
        exchange.onOffer(peerIdentity(OwnerId), offer())

        exchange.decline(TransferId)

        assertEquals(OneShotTransfer.Status.Declined, storage.oneShotTransfers.find(TransferId)!!.status)
        assertEquals(false, (ownerSession.sent.single() as FileServerMessages.OneShot.Decision).accepted)
    }

    @Test
    fun `only the receiver it was offered to can accept it`() = runTest {
        storage.oneShotTransfers.insert(outgoing())

        exchange.onDecision(peerIdentity(StrangerId), FileServerMessages.OneShot.Decision(TransferId, accepted = true))

        assertEquals(OneShotTransfer.Status.Pending, storage.oneShotTransfers.find(TransferId)!!.status)
        verify(exactly = 0) { sender.start(any()) }

        exchange.onDecision(peerIdentity(OwnerId), FileServerMessages.OneShot.Decision(TransferId, accepted = true))

        assertEquals(OneShotTransfer.Status.Active, storage.oneShotTransfers.find(TransferId)!!.status)
        verify { sender.start(TransferId) }
    }

    @Test
    fun `an offer this device made cannot be answered as if it were incoming`() = runTest {
        storage.oneShotTransfers.insert(outgoing())

        exchange.onOffer(peerIdentity(OwnerId), offer())

        assertEquals(OneShotTransfer.Direction.Outgoing, storage.oneShotTransfers.find(TransferId)!!.direction)
    }

    @Test
    fun `only the peer of a transfer can cancel it`() = runTest {
        exchange.onOffer(peerIdentity(OwnerId), offer())
        exchange.accept(TransferId, Destination)

        exchange.onCancel(peerIdentity(StrangerId), FileServerMessages.OneShot.Cancel(TransferId))
        assertEquals(OneShotTransfer.Status.Active, storage.oneShotTransfers.find(TransferId)!!.status)

        exchange.onCancel(peerIdentity(OwnerId), FileServerMessages.OneShot.Cancel(TransferId))
        assertEquals(OneShotTransfer.Status.Cancelled, storage.oneShotTransfers.find(TransferId)!!.status)
        coVerify { staging.discard(TransferId) }
    }

    @Test
    fun `cancelling here stops the work and tells the peer`() = runTest {
        storage.oneShotTransfers.insert(outgoing())

        exchange.cancel(TransferId)

        assertEquals(OneShotTransfer.Status.Cancelled, storage.oneShotTransfers.find(TransferId)!!.status)
        coVerify { sender.stop(TransferId) }
        assertTrue(ownerSession.sent.single() is FileServerMessages.OneShot.Cancel)
    }

    @Test
    fun `resuming offers again what is unanswered and sends what is accepted`() = runTest {
        storage.oneShotTransfers.insert(outgoing(id = "pending"))
        storage.oneShotTransfers.insert(outgoing(id = "active", status = OneShotTransfer.Status.Active))

        exchange.resume(OwnerId)

        assertEquals("pending", (ownerSession.sent.single() as FileServerMessages.OneShot.Offer).transferId)
        verify { sender.start("active") }
    }

    private fun offer(files: List<OneShotFileDto> = listOf(dto(0))) =
        FileServerMessages.OneShot.Offer(TransferId, senderName = "Owner", files = files)

    private fun dto(index: Int, name: String = "photo.jpg", size: Long = 10) =
        OneShotFileDto(index = index, name = name, size = size)

    private fun outgoing(id: String = TransferId, status: OneShotTransfer.Status = OneShotTransfer.Status.Pending) =
        OneShotTransfer(
            id = id,
            peer = OneShotTransfer.Peer(OwnerId, "Owner"),
            direction = OneShotTransfer.Direction.Outgoing,
            status = status,
            files = listOf(
                com.fserver.core.oneshot.model.OneShotTransferFile(
                    index = 0, name = "photo.jpg", size = 10, locator = "content://a/1",
                ),
            ),
            createdAt = clock.now(),
        )

    private companion object {
        const val OwnerId = "device-owner"
        const val StrangerId = "device-stranger"
        const val TransferId = "transfer-1"
        val Destination = SourceLocation.Downloads("FServer")
    }
}
