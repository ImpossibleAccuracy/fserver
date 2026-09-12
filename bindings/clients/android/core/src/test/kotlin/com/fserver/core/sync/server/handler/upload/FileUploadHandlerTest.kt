package com.fserver.core.sync.server.handler.upload

import android.content.ContextWrapper
import com.fserver.common.exception.FileSystemException
import com.fserver.common.exception.TransferException
import com.fserver.core.files.SourceLocation
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.RemoteOperation
import com.fserver.core.support.FakePeerSession
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.fileDto
import com.fserver.core.support.peerIdentity
import com.fserver.core.support.sourceEntry
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.progress.SyncProgressReporter
import com.fserver.core.sync.server.SessionContext
import com.fserver.core.sync.server.SourceAuthorizer
import com.fserver.files.FilesNode
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
 * The receiving end of a push, over a real directory backend.
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
    private val node = FilesNode.create(ContextWrapper(null))
    private val progress = SyncProgressReporter(clock)

    private lateinit var root: File
    private lateinit var handler: FileUploadHandler
    private lateinit var context: SessionContext

    private val owner = FakePeerSession(identity = peerIdentity(OwnerId))
    private val stranger = FakePeerSession(identity = peerIdentity(StrangerId))

    @Before
    fun setUp() = runBlocking {
        root = temp.newFolder("source-root")
        context = SessionContext(scope)
        handler = FileUploadHandler(
            authorizer = SourceAuthorizer(storage),
            storage = storage,
            node = node,
            timeProvider = clock,
            progress = progress,
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

        push(bytes)

        assertArrayEqualsFile(bytes, File(root, FilePath))

        val indexed = storage.index.findFile(IndexedFileKey(fileId = FileIdValue, sourceId = SourceId))
        assertEquals(FilePath, indexed?.path)
        assertEquals(sha256(bytes), indexed?.hash?.value)
    }

    @Test
    fun `a push into a source that syncs with another device is refused`() = runTest {
        val failure = runCatching { init(stranger) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(context.uploads.isEmpty())
        assertEquals(emptyList<File>(), root.listFiles()?.toList().orEmpty())
    }

    @Test
    fun `a path that walks out of the source writes nothing outside it`() = runTest {
        val escapee = File(temp.root, "escaped.txt")

        init(owner, path = "../escaped.txt")
        handler.queueChunk(chunk("x".toByteArray()), context)

        val failure = runCatching { complete(owner, "x".toByteArray()) }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
        assertFalse(escapee.exists())
        assertNull(storage.index.findFile(IndexedFileKey(fileId = FileIdValue, sourceId = SourceId)))
    }

    @Test
    fun `an absolute path lands inside the source rather than where it points`() = runTest {
        val outside = File(temp.root, "absolute.txt")
        val bytes = "x".toByteArray()

        init(owner, path = outside.absolutePath)
        handler.queueChunk(chunk(bytes), context)
        complete(owner, bytes)

        // The backend resolves it under the source root, so the write is contained - but it is
        // contained by nesting, not by refusal, and the row that results keeps the peer's path.
        assertFalse(outside.exists())
        assertTrue(File(root, outside.absolutePath).exists())
    }

    @Test
    fun `bytes that do not match the hash the peer promised are thrown away`() = runTest {
        init(owner)
        handler.queueChunk(chunk("tampered".toByteArray()), context)

        val failure = runCatching { complete(owner, "expected".toByteArray()) }.exceptionOrNull()

        assertTrue(failure is TransferException.UploadHashMismatchException)
        assertFalse(File(root, FilePath).exists())
        assertNull(storage.index.findFile(IndexedFileKey(fileId = FileIdValue, sourceId = SourceId)))
    }

    @Test
    fun `a chunk for an upload that was never opened is refused`() = runTest {
        val failure = runCatching { handler.queueChunk(chunk("x".toByteArray()), context) }
            .exceptionOrNull()

        assertTrue(failure is TransferException.UploadNotFoundException)
    }

    @Test
    fun `completing an upload nobody opened is refused`() = runTest {
        val failure = runCatching { complete(owner, "x".toByteArray()) }.exceptionOrNull()

        assertTrue(failure is TransferException.UploadNotFoundException)
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
            handler.handle(
                session = owner,
                operation = RemoteOperation.Upload.Init(
                    sourceId = SourceId,
                    file = fileDto(id = FileIdValue, sourceId = "other-source", path = FilePath),
                ),
                context = context,
            )
            handler.queueChunk(chunk("bytes".toByteArray()), context)
            complete(owner, "bytes".toByteArray())

            assertNull(
                storage.index.findFile(
                    IndexedFileKey(fileId = FileIdValue, sourceId = "other-source")
                )
            )
            assertEquals(
                FilePath,
                storage.index.findFile(
                    IndexedFileKey(fileId = FileIdValue, sourceId = SourceId)
                )?.path,
            )
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

        init(owner)
        handler.queueChunk(chunk("bytes".toByteArray()), context)

        val failure = runCatching {
            handler.handle(
                session = stranger,
                operation = RemoteOperation.Upload.UploadCompleted(
                    key = IndexedFileKey(fileId = FileIdValue, sourceId = SourceId),
                    hash = sha256("bytes".toByteArray()),
                    algorithm = "SHA-256",
                ),
                context = context,
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertNull(storage.index.findFile(IndexedFileKey(fileId = FileIdValue, sourceId = SourceId)))
    }

    @Test
    fun `bytes beyond the size the peer declared are refused`() {
        // TODO: nothing enforces `Init.file.metadata.size` today - a sender that declares 1 KiB
        //  and then streams until the disk is full is accepted. Decide where the cap belongs
        //  (UploadContext.offer, so it is refused before the buffer) and assert it here.
    }

    @Test
    fun `a file id that does not match the path it is written to is refused`() {
        // TODO: `fileId` is cross-device identity and is derived from the canonical path
        //  (SourcePaths.fileId), but the receiver takes the peer's word for both. That lets one
        //  file's bytes be indexed under another file's identity. Either derive the id here or
        //  refuse a mismatch - then assert it.
    }

    private suspend fun push(bytes: ByteArray) {
        init(owner)
        handler.queueChunk(chunk(bytes), context)
        complete(owner, bytes)
    }

    private suspend fun init(
        session: FakePeerSession,
        sourceId: String = SourceId,
        path: String = FilePath,
    ) = handler.handle(
        session = session,
        operation = RemoteOperation.Upload.Init(
            sourceId = sourceId,
            file = fileDto(id = FileIdValue, sourceId = sourceId, path = path),
        ),
        context = context,
    )

    private suspend fun complete(session: FakePeerSession, bytes: ByteArray) = handler.handle(
        session = session,
        operation = RemoteOperation.Upload.UploadCompleted(
            key = IndexedFileKey(fileId = FileIdValue, sourceId = SourceId),
            hash = sha256(bytes),
            algorithm = "SHA-256",
        ),
        context = context,
    )

    private fun chunk(bytes: ByteArray) = FileServerMessages.UploadChunk(
        sourceId = SourceId,
        fileId = FileIdValue,
        offset = 0,
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
