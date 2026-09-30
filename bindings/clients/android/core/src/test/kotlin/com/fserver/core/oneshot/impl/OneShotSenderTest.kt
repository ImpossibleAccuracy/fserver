package com.fserver.core.oneshot.impl

import com.fserver.common.exception.FileSystemException
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.FileServerMessages.Upload
import com.fserver.core.oneshot.model.OneShotTransfer
import com.fserver.core.oneshot.model.OneShotTransferFile
import com.fserver.core.support.FakePeerSession
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.peerIdentity
import com.fserver.core.sync.remote.PeerConnector
import com.fserver.files.FilesNode
import com.fserver.core.sync.progress.FileTransfer
import com.fserver.core.sync.transfer.FilePusher
import com.fserver.core.sync.progress.impl.SyncProgressReporter
import com.fserver.files.fs.FileSystem
import com.fserver.files.fs.FsFile
import kotlinx.coroutines.flow.first
import com.fserver.files.fs.FsWriter
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import kotlin.time.Instant

/** The sending end, against a scripted receiver. */
class OneShotSenderTest {

    private val clock = MutableTimeProvider()
    private val storage = FakeStorage(clock = clock)
    private val peers = mockk<PeerConnector>()
    private val node = mockk<FilesNode>()
    private val background = TestScope()

    /** What the receiver holds; [answer] plays its side. */
    private val received = ByteArrayOutputStream()
    private var answer: (FileServerMessages) -> FileServerMessages? = ::honestReceiver

    private val session = FakePeerSession(
        identity = peerIdentity(PeerId),
        maxPayloadSize = 64,
        responder = { answer(it) },
    )

    private val progress = SyncProgressReporter(clock)
    private val pusher = FilePusher(progress)
    private val sender = OneShotSender(storage, node, peers, pusher, clock, background)

    init {
        coEvery { peers.connectToDevice(PeerId) } returns session
    }

    /** The shared uris, as the Shared backend would open them. */
    private fun shares(file: FsFile) {
        every { node.openSource(any()) } returns mockk<FileSystem> {
            coEvery { openFile(any()) } returns file
        }
    }

    @Test
    fun `every byte goes out in order and the transfer completes`() = runTest {
        shares(BytesFile(Content))
        storage.oneShotTransfers.insert(active())

        sender.start(TransferId)
        background.advanceUntilIdle()

        assertArrayEquals(Content, received.toByteArray())
        val transfer = storage.oneShotTransfers.find(TransferId)!!
        assertEquals(OneShotTransfer.Status.Completed, transfer.status)
        assertEquals(OneShotTransferFile.Status.Completed, transfer.files.single().status)

        val reported = progress.oneShotTransfers.first().single()
        assertEquals(FileTransfer.Direction.Outgoing, reported.direction)
        assertEquals(FileTransfer.State.Completed, reported.state)
    }

    @Test
    fun `a file that cannot be read anymore is skipped, and the receiver told`() = runTest {
        shares(BytesFile(Content, readable = false))
        storage.oneShotTransfers.insert(active())

        sender.start(TransferId)
        background.advanceUntilIdle()

        assertTrue(session.requested.any { it is Upload.Abandon })
        val transfer = storage.oneShotTransfers.find(TransferId)!!
        assertTrue(transfer.files.single().status is OneShotTransferFile.Status.Failed)
        assertTrue(transfer.status is OneShotTransfer.Status.Failed)
    }

    @Test
    fun `a receiver that stopped the transfer ends it here`() = runTest {
        shares(BytesFile(Content))
        storage.oneShotTransfers.insert(active())
        answer = { Upload.Stopped((it as Upload).key, "Transfer is Cancelled") }

        sender.start(TransferId)
        background.advanceUntilIdle()

        assertTrue(storage.oneShotTransfers.find(TransferId)!!.status is OneShotTransfer.Status.Failed)
        assertTrue(session.sent.isEmpty())
    }

    @Test
    fun `a resumed file sends only what the receiver lacks`() = runTest {
        shares(BytesFile(Content))
        storage.oneShotTransfers.insert(active())
        received.write(Content, 0, 10)

        sender.start(TransferId)
        background.advanceUntilIdle()

        assertArrayEquals(Content, received.toByteArray())
        val first = session.sent.filterIsInstance<FileServerMessages.UploadChunk>().first()
        assertEquals(10L, first.offset)
    }

    @Test
    fun `a transfer that is not accepted is not sent`() = runTest {
        shares(BytesFile(Content))
        storage.oneShotTransfers.insert(active().copy(status = OneShotTransfer.Status.Pending))

        sender.start(TransferId)
        background.advanceUntilIdle()

        assertTrue(session.requested.isEmpty())
    }

    /** Folds in the chunks sent since it last answered, then answers like a receiver would. */
    private fun honestReceiver(message: FileServerMessages): FileServerMessages? {
        session.sent.filterIsInstance<FileServerMessages.UploadChunk>().forEach { chunk ->
            if (chunk.offset == received.size().toLong()) received.write(chunk.bytes)
        }

        return when (message) {
            is Upload.Init, is Upload.Status ->
                Upload.Received((message as Upload).key, received.size().toLong())

            is Upload.Abandon -> Upload.Completed(message.key)

            is Upload.Complete ->
                if (message.hash == sha256(received.toByteArray())) Upload.Completed(message.key)
                else Upload.Failed(message.key, "Hash mismatch")

            else -> null
        }
    }

    private fun active() = OneShotTransfer(
        id = TransferId,
        peer = OneShotTransfer.Peer(PeerId, "Peer"),
        direction = OneShotTransfer.Direction.Outgoing,
        status = OneShotTransfer.Status.Active,
        files = listOf(
            OneShotTransferFile(index = 0, name = "a.bin", size = Content.size.toLong(), locator = "content://a/1"),
        ),
        createdAt = clock.now(),
    )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private class BytesFile(private val content: ByteArray, private val readable: Boolean = true) : FsFile {
        override val locator = "content://a/1"
        override suspend fun read(): InputStream =
            if (readable) ByteArrayInputStream(content) else throw FileSystemException.InvalidPath(locator)

        override suspend fun openWriter(): FsWriter = throw UnsupportedOperationException()
        override suspend fun rename(newName: String, deleteOldOnConflict: Boolean): FsFile = this
        override suspend fun delete(): Boolean = false
        override suspend fun settleLastModified(time: Instant): Instant = time
    }

    private companion object {
        const val PeerId = "device-peer"
        const val TransferId = "transfer-1"

        /** Bigger than one chunk at a 64-byte frame, so it takes several. */
        val Content = ByteArray(10_000) { (it % 251).toByte() }
    }
}
