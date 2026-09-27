package com.fserver.core.sync.server.handler.upload

import android.content.ContextWrapper
import com.fserver.common.model.FileSize
import com.fserver.core.files.SourceLocation
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.FileServerMessages.Upload
import com.fserver.core.support.FakePeerSession
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.TestEpoch
import com.fserver.core.support.fileDto
import com.fserver.core.support.indexedFile
import com.fserver.core.support.peerIdentity
import com.fserver.core.support.sourceEntry
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import com.fserver.core.sync.progress.SyncProgressReporter
import com.fserver.core.sync.server.SessionContext
import com.fserver.core.sync.runner.RequestedDownloads
import com.fserver.core.sync.server.SourceAuthorizer
import com.fserver.files.FilesNode
import com.fserver.net.session.PeerSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

/**
 * The receiving end of a push, over a real directory backend and a real staging directory.
 *
 * Everything the peer sends is peer-chosen - the source id, the file id, the path, the offsets and
 * the hash - so each of those is a way in, and each is checked here rather than assumed.
 */
class FileUploadHandlerTest {

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clock = MutableTimeProvider()
    private val storage = FakeStorage(clock = clock)
    private val progress = SyncProgressReporter(clock)

    private lateinit var root: File
    private lateinit var stagingDir: File
    private lateinit var staging: UploadStaging
    private lateinit var handler: FileUploadHandler
    private lateinit var context: SessionContext

    private val owner = FakePeerSession(identity = peerIdentity(OwnerId))
    private val stranger = FakePeerSession(identity = peerIdentity(StrangerId))

    private val key = IndexedFileKey(fileId = FileIdValue, sourceId = SourceId)
    private val requested = RequestedDownloads()

    @Before
    fun setUp() = runBlocking {
        root = temp.newFolder("source-root")
        stagingDir = temp.newFolder("staging")
        context = SessionContext(scope)

        val node = FilesNode.create(ContextWrapper(null), stagingDir = stagingDir)
        staging = UploadStaging(storage, node, clock)
        handler = FileUploadHandler(
            authorizer = SourceAuthorizer(storage),
            storage = storage,
            node = node,
            staging = staging,
            timeProvider = clock,
            progress = progress,
            requestedDownloads = requested,
        )

        storage.sources.upsert(
            sourceEntry(
                id = SourceId,
                deviceId = OwnerId,
                location = SourceLocation.Directory(root.absolutePath),
            )
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `a full push lands as a file and one index row`() = runTest {
        val bytes = "the actual file".toByteArray()

        assertTrue(push(bytes) is Upload.Completed)

        assertArrayEqualsFile(bytes, File(root, FilePath))

        val indexed = storage.index.findFile(key)
        assertEquals(FilePath, indexed?.path)
        assertEquals(sha256(bytes), indexed?.hash?.value)
    }

    @Test
    fun `a finished push leaves nothing staged`() = runTest {
        push("the actual file".toByteArray())

        assertNull(storage.uploads.find(key))
        assertTrue(stagingDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun `an empty file is pushed without a single chunk`() = runTest {
        init(owner, size = 0)

        assertTrue(complete(owner, ByteArray(0)) is Upload.Completed)
        assertEquals(0, File(root, FilePath).length())
    }

    @Test
    fun `a push over an existing file replaces it`() = runTest {
        File(root, FilePath).writeText("old")

        push("new content".toByteArray())

        assertEquals("new content", File(root, FilePath).readText())
        assertFalse(File(root, "$FilePath.temp").exists())
    }

    @Test
    fun `a received file keeps the sender's mtime, so the next scan does not read it as an edit`() =
        runTest {
            push("the actual file".toByteArray())

            val onDisk = File(root, FilePath).lastModified()
            val indexed = storage.index.findFile(key)

            assertEquals(TestEpoch.toEpochMilliseconds(), onDisk)
            assertEquals(onDisk, indexed?.modifiedAt?.toEpochMilliseconds())
            assertFalse(indexed!!.hashStale)
        }

    @Test
    fun `an upload the session dropped resumes where it stopped`() = runTest {
        val bytes = "hello world".toByteArray()

        init(owner, size = bytes.size.toLong())
        handler.queueChunk(chunk(bytes.copyOfRange(0, 6)), context)
        awaitStaged(6)

        handler.sessionEnded(context)
        context = SessionContext(scope)

        val resumed = init(owner, size = bytes.size.toLong())
        assertEquals(6L, (resumed as Upload.Received).offset)

        handler.queueChunk(chunk(bytes.copyOfRange(6, 11), offset = 6), context)

        assertTrue(complete(owner, bytes) is Upload.Completed)
        assertArrayEqualsFile(bytes, File(root, FilePath))
    }

    @Test
    fun `another version of the file starts over instead of resuming`() = runTest {
        init(owner, size = 11)
        handler.queueChunk(chunk("hello ".toByteArray()), context)
        awaitStaged(6)
        handler.sessionEnded(context)
        context = SessionContext(scope)

        val restarted = init(owner, size = 12)

        assertEquals(0L, (restarted as Upload.Received).offset)
    }

    @Test
    fun `a push whose placement failed is placed again without a byte resent`() = runTest {
        val bytes = "the actual file".toByteArray()
        // A directory in the way: the source refuses to put a file there.
        File(root, FilePath).mkdirs()

        assertTrue(push(bytes) is Upload.Failed)

        File(root, FilePath).delete()
        context = SessionContext(scope)

        val resumed = init(owner, size = bytes.size.toLong())
        assertEquals(bytes.size.toLong(), (resumed as Upload.Received).offset)

        assertTrue(complete(owner, bytes) is Upload.Completed)
        assertArrayEqualsFile(bytes, File(root, FilePath))
    }

    @Test
    fun `status is a checkpoint the upload survives a crash from`() = runTest {
        init(owner, size = 100)
        handler.queueChunk(chunk("0123456789".toByteArray()), context)
        awaitStaged(10)

        val status = ask(owner, Upload.Status(key))

        assertEquals(10L, (status as Upload.Received).offset)
        assertEquals(10L, storage.uploads.find(key)?.committedOffset)
    }

    @Test
    fun `status for an upload the receiver lost fails, so the sender inits again`() = runTest {
        assertTrue(ask(owner, Upload.Status(key)) is Upload.Failed)
    }

    @Test
    fun `completing with bytes missing answers where to resume from`() = runTest {
        init(owner, size = 10)
        handler.queueChunk(chunk("01234".toByteArray()), context)

        val answer = complete(owner, "0123456789".toByteArray())

        assertEquals(5L, (answer as Upload.Received).offset)
        assertTrue(context.uploads.isEmpty())
        assertFalse(File(root, FilePath).exists())
    }

    @Test
    fun `a push into a source that syncs with another device is refused`() = runTest {
        assertTrue(init(stranger) is Upload.Failed)

        assertTrue(context.uploads.isEmpty())
        assertEquals(emptyList<File>(), root.listFiles()?.toList().orEmpty())
        assertTrue(stagingDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun `a one-way initiator takes a push only as a download it asked for`() = runTest {
        val source = storage.sources.findById(SourceId)!!
        storage.sources.upsert(
            source.copy(syncMode = SyncMode.AutoUpload(ignoreFilesBefore = null), role = SourceEntry.Role.Initiator)
        )

        assertTrue(init(owner) is Upload.Failed)
        assertTrue(context.uploads.isEmpty())

        requested.awaiting(OwnerId, key) {
            assertTrue(init(owner) is Upload.Received)
        }
    }

    @Test
    fun `a path that walks out of the source writes nothing outside it`() = runTest {
        val escapee = File(temp.root, "escaped.txt")

        // Refused at Init: checking for an existing file already resolves the path.
        val answer = init(owner, path = "../escaped.txt")

        assertTrue(answer is Upload.Failed)
        assertFalse(escapee.exists())
        assertNull(storage.index.findFile(key))
    }

    @Test
    fun `a file id that walks out of its staging directory is refused`() = runTest {
        val answer = ask(
            owner,
            Upload.Init(
                sourceId = SourceId,
                file = fileDto(id = "../other-source/x", sourceId = SourceId, path = FilePath, size = 1),
            ),
        )

        assertTrue(answer is Upload.Failed)
        assertTrue(stagingDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun `an absolute path lands inside the source rather than where it points`() = runTest {
        val outside = File(temp.root, "absolute.txt")
        val bytes = "x".toByteArray()

        init(owner, path = outside.absolutePath, size = 1)
        handler.queueChunk(chunk(bytes), context)
        complete(owner, bytes)

        // The backend resolves it under the source root, so the write is contained - but it is
        // contained by nesting, not by refusal, and the row that results keeps the peer's path.
        assertFalse(outside.exists())
        assertTrue(File(root, outside.absolutePath).exists())
    }

    @Test
    fun `bytes that do not match the hash the peer promised are thrown away`() = runTest {
        init(owner, size = 8)
        handler.queueChunk(chunk("tampered".toByteArray()), context)

        val answer = complete(owner, "expected".toByteArray())

        assertTrue(answer is Upload.Failed)
        assertFalse(File(root, FilePath).exists())
        assertNull(storage.index.findFile(key))
        assertNull(storage.uploads.find(key))
    }

    @Test
    fun `a chunk for an upload that was never opened is refused`() = runTest {
        val failure = runCatching { handler.queueChunk(chunk("x".toByteArray()), context) }
            .exceptionOrNull()

        assertTrue(failure is com.fserver.common.exception.TransferException.UploadNotFoundException)
    }

    @Test
    fun `completing an upload nobody opened is refused`() = runTest {
        assertTrue(complete(owner, "x".toByteArray()) is Upload.Failed)
    }

    @Test
    fun `the source the record claims does not override the source that was authorized`() =
        runTest {
            storage.sources.upsert(
                sourceEntry(
                    id = "other-source",
                    deviceId = OwnerId,
                    location = SourceLocation.Directory(temp.newFolder("other-root").absolutePath),
                )
            )

            // Authorized for SourceId, but the record inside names another source this peer also
            // owns. The row must land under the id that was checked, not the one that was claimed.
            ask(
                owner,
                Upload.Init(
                    sourceId = SourceId,
                    file = fileDto(id = FileIdValue, sourceId = "other-source", path = FilePath, size = 5),
                ),
            )
            handler.queueChunk(chunk("bytes".toByteArray()), context)
            complete(owner, "bytes".toByteArray())

            assertNull(storage.index.findFile(IndexedFileKey(fileId = FileIdValue, sourceId = "other-source")))
            assertEquals(FilePath, storage.index.findFile(key)?.path)
        }

    @Test
    fun `completing an upload opened under another peer's source is refused`() = runTest {
        storage.sources.upsert(
            sourceEntry(
                id = "strangers-source",
                deviceId = StrangerId,
                location = SourceLocation.Directory(temp.newFolder("stranger-root").absolutePath),
            )
        )

        init(owner, size = 5)
        handler.queueChunk(chunk("bytes".toByteArray()), context)

        assertTrue(complete(stranger, "bytes".toByteArray()) is Upload.Failed)
        assertNull(storage.index.findFile(key))
    }

    @Test
    fun `bytes beyond the size the peer declared are refused`() = runTest {
        init(owner, size = 2)
        handler.queueChunk(chunk("too long".toByteArray()), context)

        assertTrue(complete(owner, "too long".toByteArray()) is Upload.Failed)
        assertFalse(File(root, FilePath).exists())
    }

    @Test
    fun `a file id that does not match the path it is written to is refused`() {
        // TODO: `fileId` is cross-device identity and is derived from the canonical path
        //  (SourcePaths.fileId), but the receiver takes the peer's word for both. That lets one
        //  file's bytes be indexed under another file's identity. Either derive the id here or
        //  refuse a mismatch - then assert it.
    }

    @Test
    fun `a new file past our file count is declined before anything is staged`() = runTest {
        limitSource(SourceEntry.Preferences.FileLimits(maxFiles = 1, maxTotalSize = null))
        storage.index.markProcessed(listOf(indexedFile(fileId = "other", sourceId = SourceId)))

        assertTrue(init(owner) is Upload.OverLimit)
        assertTrue(context.uploads.isEmpty())
        assertNull(storage.uploads.find(key))
    }

    @Test
    fun `a new file past our total size is declined`() = runTest {
        limitSource(SourceEntry.Preferences.FileLimits(maxFiles = null, maxTotalSize = FileSize(10)))

        assertTrue(init(owner, size = 15) is Upload.OverLimit)
    }

    @Test
    fun `an update to a file we hold is taken at the file count limit`() = runTest {
        limitSource(SourceEntry.Preferences.FileLimits(maxFiles = 1, maxTotalSize = FileSize(20)))
        storage.index.markProcessed(listOf(indexedFile(fileId = FileIdValue, sourceId = SourceId, size = 1)))

        assertTrue(init(owner, size = 15) is Upload.Received)
    }

    @Test
    fun `a file sent small cannot grow past our total size`() = runTest {
        limitSource(SourceEntry.Preferences.FileLimits(maxFiles = null, maxTotalSize = FileSize(20)))
        storage.index.markProcessed(listOf(indexedFile(fileId = FileIdValue, sourceId = SourceId, size = 10)))

        assertTrue(init(owner, size = 21) is Upload.OverLimit)
    }

    @Test
    fun `an upload still open on the session holds its room`() = runTest {
        limitSource(SourceEntry.Preferences.FileLimits(maxFiles = 1, maxTotalSize = null))
        assertTrue(init(owner) is Upload.Received)

        val second = Upload.Init(
            sourceId = SourceId,
            file = fileDto(id = "file-2", sourceId = SourceId, path = "other.jpg", size = 1),
        )

        assertTrue(ask(owner, second) is Upload.OverLimit)
    }

    private suspend fun limitSource(limits: SourceEntry.Preferences.FileLimits) {
        val source = storage.sources.findById(SourceId)!!
        storage.sources.upsert(
            source.copy(preferences = SourceEntry.Preferences.Default.copy(fileLimits = limits))
        )
    }

    private suspend fun push(bytes: ByteArray): Upload {
        init(owner, size = bytes.size.toLong())
        handler.queueChunk(chunk(bytes), context)
        return complete(owner, bytes)
    }

    private suspend fun init(
        session: FakePeerSession,
        path: String = FilePath,
        size: Long = 15,
    ): Upload = ask(
        session,
        Upload.Init(
            sourceId = SourceId,
            file = fileDto(id = FileIdValue, sourceId = SourceId, path = path, size = size),
        ),
    )

    private suspend fun complete(session: FakePeerSession, bytes: ByteArray): Upload = ask(
        session,
        Upload.Complete(key = key, hash = sha256(bytes), algorithm = "SHA-256"),
    )

    private suspend fun ask(session: FakePeerSession, message: Upload): Upload {
        val replies = FakePeerSession.Replies()
        handler.handle(PeerSession.Inbound(message, replies.channel), message, session, context)
        return replies.only()
    }

    /** Chunks are written off the collector; a test that parks the upload waits for them first. */
    private fun awaitStaged(bytes: Long) {
        val upload = context.uploads.getValue(key)
        val deadline = System.currentTimeMillis() + 5_000

        while (upload.prefix < bytes) {
            check(System.currentTimeMillis() < deadline) { "staged ${upload.prefix} of $bytes" }
            Thread.sleep(10)
        }
    }

    private fun chunk(bytes: ByteArray, offset: Long = 0) = FileServerMessages.UploadChunk(
        sourceId = SourceId,
        fileId = FileIdValue,
        offset = offset,
        bytes = bytes,
    )

    private fun assertArrayEqualsFile(expected: ByteArray, file: File) {
        assertTrue("${file.path} does not exist", file.exists())
        assertEquals(expected.toList(), file.readBytes().toList())
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val SourceId = "source-1"
        const val OwnerId = "device-owner"
        const val StrangerId = "device-stranger"
        const val FileIdValue = "file-1"
        const val FilePath = "photo.jpg"
    }
}
