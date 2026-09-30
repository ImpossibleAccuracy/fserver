package com.fserver.core.sync.server.handler.upload.oneshot

import android.content.ContextWrapper
import com.fserver.common.exception.TransferException
import com.fserver.core.files.SourceLocation
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.FileServerMessages.Upload
import com.fserver.core.network.dictionary.dto.UploadKey
import com.fserver.core.oneshot.model.OneShotTransfer
import com.fserver.core.oneshot.model.OneShotTransferFile
import com.fserver.core.support.FakePeerSession
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.peerIdentity
import com.fserver.core.sync.progress.FileTransfer
import com.fserver.core.sync.progress.impl.SyncProgressReporter
import com.fserver.core.sync.server.handler.upload.FileUploadHandler
import com.fserver.core.sync.server.SessionContext
import com.fserver.files.FilesNode
import com.fserver.net.session.PeerSession
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

/**
 * The receiving end of a one-shot transfer, through the same [FileUploadHandler] a source's file goes
 * through, over a real destination and staging directory. The
 * transfer id, the index, the name, the offsets and the hash all come from the peer.
 */
class UploadTargetTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clock = MutableTimeProvider()
    private val storage = FakeStorage(clock = clock)
    private val progress = SyncProgressReporter(clock)

    private lateinit var root: File
    private lateinit var stagingDir: File
    private lateinit var staging: OneShotStaging
    private lateinit var handler: FileUploadHandler

    /** One context per session, as the server keeps them. */
    private val contexts = mutableMapOf<FakePeerSession, SessionContext>()

    private val owner = FakePeerSession(identity = peerIdentity(OwnerId))
    private val stranger = FakePeerSession(identity = peerIdentity(StrangerId))

    @Before
    fun setUp() = runBlocking {
        root = temp.newFolder("downloads")
        stagingDir = temp.newFolder("staging")

        val node = FilesNode.create(ContextWrapper(null), stagingDir = stagingDir)
        staging = OneShotStaging(node)
        handler = FileUploadHandler(
            sources = mockk(relaxed = true),
            oneShots = OneShotUploadTarget(storage, node, staging, clock),
            timeProvider = clock,
            progress = progress,
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `a whole file lands in the destination under its name and settles the transfer`() = runTest {
        accepted(file(0, "photo.jpg", Content))

        assertTrue(push(0, Content) is Upload.Completed)

        assertEquals(String(Content), File(root, "photo.jpg").readText())
        val transfer = storage.oneShotTransfers.find(TransferId)!!
        assertEquals(OneShotTransfer.Status.Completed, transfer.status)
        assertEquals(OneShotTransferFile.Status.Completed, transfer.files.single().status)
        assertTrue(stagingDir.walkTopDown().none { it.isFile })

        val reported = progress.oneShotTransfers.first().single()
        assertEquals(TransferId, reported.transferId)
        assertEquals(FileTransfer.State.Completed, reported.state)
        assertTrue(progress.transfers.first().isEmpty())
    }

    @Test
    fun `a received file never replaces one already there`() = runTest {
        File(root, "photo.jpg").writeText("mine")
        accepted(file(0, "photo.jpg", Content))

        push(0, Content)

        assertEquals("mine", File(root, "photo.jpg").readText())
        assertEquals(String(Content), File(root, "photo (1).jpg").readText())
    }

    @Test
    fun `a name that walks out of the destination stays inside it`() = runTest {
        accepted(file(0, "../../evil.sh", Content))

        push(0, Content)

        assertTrue(File(root, "evil.sh").isFile)
        assertEquals(listOf("evil.sh"), root.list()!!.toList())
    }

    @Test
    fun `another device cannot push into the transfer`() = runTest {
        accepted(file(0, "photo.jpg", Content))

        val answer = ask(stranger, Upload.Init(key(0)))

        assertTrue(answer is Upload.Stopped)
        assertTrue(stagingDir.walkTopDown().none { it.isFile })
    }

    @Test
    fun `chunks feed only the session that opened the file`() = runTest {
        accepted(file(0, "photo.jpg", Content))
        ask(owner, Upload.Init(key(0)))

        val failure = runCatching { queue(stranger, chunk(0, 0, Content)) }.exceptionOrNull()

        assertTrue(failure is TransferException.UploadNotFoundException)
    }

    @Test
    fun `nothing is taken before the user accepts`() = runTest {
        storage.oneShotTransfers.insert(incoming(OneShotTransfer.Status.Pending, destination = null, file(0, "a.txt", Content)))

        assertTrue(ask(owner, Upload.Init(key(0))) is Upload.Stopped)
    }

    @Test
    fun `broken bytes are refused and the file can be sent again`() = runTest {
        accepted(file(0, "a.txt", Content))

        assertTrue(push(0, Content, hash = sha256("something else".toByteArray())) is Upload.Failed)
        assertFalse(File(root, "a.txt").exists())

        assertTrue(push(0, Content) is Upload.Completed)
        assertEquals(String(Content), File(root, "a.txt").readText())
    }

    @Test
    fun `a chunk past the declared size fails the file, not the session`() = runTest {
        accepted(file(0, "a.txt", Content))

        ask(owner, Upload.Init(key(0)))
        queue(owner, chunk(0, Content.size.toLong(), "extra".toByteArray()))

        val answer = ask(owner, Upload.Complete(key(0), sha256(Content), Algorithm))

        assertTrue(answer is Upload.Failed)
        assertFalse(File(root, "a.txt").exists())
    }

    @Test
    fun `a dropped session resumes from the checkpoint`() = runTest {
        accepted(file(0, "a.txt", Content))
        val half = Content.size / 2

        ask(owner, Upload.Init(key(0)))
        queue(owner, chunk(0, 0, Content.copyOfRange(0, half)))
        assertEquals(half.toLong(), checkpointOnce(half.toLong()))
        handler.sessionEnded(contextOf(owner))

        val reconnected = FakePeerSession(identity = peerIdentity(OwnerId))
        val resumed = ask(reconnected, Upload.Init(key(0))) as Upload.Received
        assertEquals(half.toLong(), resumed.offset)

        queue(reconnected, chunk(0, half.toLong(), Content.copyOfRange(half, Content.size)))
        assertTrue(ask(reconnected, Upload.Complete(key(0), sha256(Content), Algorithm)) is Upload.Completed)
        assertEquals(String(Content), File(root, "a.txt").readText())
    }

    @Test
    fun `a skipped file fails alone, and a transfer with nothing received fails`() = runTest {
        accepted(file(0, "a.txt", Content), file(1, "b.txt", Content))

        ask(owner, Upload.Abandon(key(0), "gone"))
        assertEquals(OneShotTransfer.Status.Active, storage.oneShotTransfers.find(TransferId)!!.status)

        push(1, Content)
        assertEquals(OneShotTransfer.Status.Completed, storage.oneShotTransfers.find(TransferId)!!.status)

        storage.oneShotTransfers.rows.value = emptyMap()
        accepted(file(0, "a.txt", Content))
        ask(owner, Upload.Abandon(key(0), "gone"))
        assertTrue(storage.oneShotTransfers.find(TransferId)!!.status is OneShotTransfer.Status.Failed)
    }

    @Test
    fun `a cancelled transfer stops at the next request and leaves nothing staged`() = runTest {
        accepted(file(0, "a.txt", Content))
        ask(owner, Upload.Init(key(0)))
        queue(owner, chunk(0, 0, Content.copyOfRange(0, 3)))

        // What cancelling does on this side: the record, then the staged bytes.
        storage.oneShotTransfers.updateStatus(TransferId, OneShotTransfer.Status.Cancelled, clock.now())
        staging.discard(TransferId)

        assertTrue(ask(owner, Upload.Status(key(0))) is Upload.Stopped)
        assertTrue(contextOf(owner).uploads.inFlight.isEmpty())
        assertTrue(ask(owner, Upload.Init(key(0))) is Upload.Stopped)
        assertTrue(stagingDir.walkTopDown().none { it.isFile })
    }

    private suspend fun accepted(vararg files: OneShotTransferFile) {
        storage.oneShotTransfers.insert(
            incoming(OneShotTransfer.Status.Active, SourceLocation.Directory(root.absolutePath), *files)
        )
    }

    private fun incoming(
        status: OneShotTransfer.Status,
        destination: SourceLocation.Hostable?,
        vararg files: OneShotTransferFile,
    ) = OneShotTransfer(
        id = TransferId,
        peer = OneShotTransfer.Peer(OwnerId, "Owner"),
        direction = OneShotTransfer.Direction.Incoming(destination),
        status = status,
        files = files.toList(),
        createdAt = clock.now(),
    )

    private fun file(index: Int, name: String, content: ByteArray) = OneShotTransferFile(
        index = index,
        name = name,
        size = content.size.toLong(),
        locator = null,
    )

    private suspend fun push(index: Int, content: ByteArray, hash: String = sha256(content)): Upload {
        ask(owner, Upload.Init(key(index)))
        queue(owner, chunk(index, 0, content))
        return ask(owner, Upload.Complete(key(index), hash, Algorithm))
    }

    private suspend fun ask(session: FakePeerSession, message: Upload): Upload {
        val replies = FakePeerSession.Replies()
        handler.handle(PeerSession.Inbound(message, replies.channel), message, session, contextOf(session))
        return replies.only()
    }

    /** The writer is off the collector: ask until what it wrote is checkpointed. */
    private suspend fun checkpointOnce(expected: Long): Long {
        repeat(200) {
            val offset = (ask(owner, Upload.Status(key(0))) as Upload.Received).offset
            if (offset == expected) return offset
            withContext(Dispatchers.Default) { delay(10) }
        }
        error("Never checkpointed $expected")
    }

    private fun queue(session: FakePeerSession, chunk: FileServerMessages.UploadChunk) =
        handler.queueChunk(chunk, contextOf(session))

    private fun contextOf(session: FakePeerSession) = contexts.getOrPut(session) {
        SessionContext(scope, handler.sessionUploads(scope))
    }

    private fun key(index: Int) = UploadKey.OneShot(TransferId, index)

    private fun chunk(index: Int, offset: Long, bytes: ByteArray) =
        FileServerMessages.UploadChunk(key(index), offset, bytes)

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val OwnerId = "device-owner"
        const val StrangerId = "device-stranger"
        const val TransferId = "transfer-1"
        const val Algorithm = "SHA-256"
        val Content = "one-shot file content".toByteArray()
    }
}
