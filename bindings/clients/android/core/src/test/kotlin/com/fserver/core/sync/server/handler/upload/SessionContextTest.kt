package com.fserver.core.sync.server.handler.upload

import android.content.ContextWrapper
import com.fserver.common.exception.TransferException
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.InMemoryFileSystem
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.TestEpoch
import com.fserver.core.support.sourceEntry
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.progress.SyncProgressReporter
import com.fserver.core.sync.server.SessionContext
import com.fserver.files.FilesNode
import com.fserver.files.upload.FileId
import com.fserver.files.upload.FileRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.time.Duration.Companion.minutes

/** What one peer may have open at once, and what happens to what it left unfinished. */
class SessionContextTest {

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clock = MutableTimeProvider()
    private val storage = FakeStorage(clock = clock)
    private val fs = InMemoryFileSystem()
    private val progress = SyncProgressReporter(clock)
    private val context = SessionContext(scope)
    private val source = sourceEntry(id = "source-1")

    private lateinit var stagingDir: File
    private lateinit var staging: UploadStaging

    @Before
    fun setUp() = runBlocking {
        stagingDir = temp.newFolder("staging")
        val node = FilesNode.create(ContextWrapper(null), stagingDir = stagingDir)
        staging = UploadStaging(storage, node, clock)
        storage.sources.upsert(source)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `a peer may not open more uploads than the session allows`() = runTest {
        repeat(SessionContext.MaxConcurrentUploads) { open("file-$it") }

        val failure = runCatching { open("one-too-many") }.exceptionOrNull()

        assertTrue(failure is TransferException.TooManyUploadsException)
        assertEquals(SessionContext.MaxConcurrentUploads, context.uploads.size)
    }

    @Test
    fun `re-opening the same file resumes from what the first attempt wrote`() = runTest {
        val first = open("file-1")
        first.offer(chunk("file-1", "half".toByteArray()))
        first.await()

        val second = open("file-1")

        assertEquals(4, second.prefix)
        assertEquals(1, context.uploads.size)
        assertEquals("half", stagedBytes("file-1"))
    }

    @Test
    fun `an upload whose sender went quiet is parked with its staged bytes`() = runTest {
        val stale = open("file-1")
        stale.offer(chunk("file-1", "half".toByteArray()))
        stale.await()

        context.pruneStaleUploads(TestEpoch + SessionContext.UploadTimeout + 1.minutes, staging)

        assertTrue(context.uploads.isEmpty())
        assertEquals("half", stagedBytes("file-1"))
        assertEquals(4L, storage.uploads.find(key("file-1"))?.committedOffset)
    }

    @Test
    fun `an upload still inside its timeout is left alone`() = runTest {
        open("file-1")

        context.pruneStaleUploads(TestEpoch + SessionContext.UploadTimeout - 1.minutes, staging)

        assertEquals(1, context.uploads.size)
    }

    @Test
    fun `a session that ends parks every open upload`() = runTest {
        val upload = open("file-1")
        upload.offer(chunk("file-1", "half".toByteArray()))
        upload.await()

        context.parkAll(staging)

        assertTrue(context.uploads.isEmpty())
        assertEquals(4L, storage.uploads.find(key("file-1"))?.committedOffset)
    }

    private suspend fun open(fileId: String): UploadContext =
        context.start(
            source = source,
            file = FileRecord(
                id = FileId(fileId),
                path = "$fileId.bin",
                locator = null,
                state = FileRecord.State.Present(),
                content = null,
                metadata = FileRecord.Metadata(
                    size = 100,
                    lastModified = TestEpoch,
                    version = null,
                ),
            ),
            deviceId = source.deviceId,
            fs = fs,
            staging = staging,
            startedAt = TestEpoch,
            progress = progress,
        )

    private fun key(fileId: String) = IndexedFileKey(fileId = fileId, sourceId = source.id)

    private fun stagedBytes(fileId: String): String =
        File(stagingDir, "${source.id}/$fileId/data").readText()

    private fun chunk(fileId: String, bytes: ByteArray) = FileServerMessages.UploadChunk(
        sourceId = source.id,
        fileId = fileId,
        offset = 0,
        bytes = bytes,
    )
}
