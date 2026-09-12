package com.fserver.core.sync.server.handler.upload

import com.fserver.common.exception.TransferException
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.support.InMemoryFileSystem
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.TestEpoch
import com.fserver.core.support.sourceEntry
import com.fserver.core.sync.progress.SyncProgressReporter
import com.fserver.core.sync.server.SessionContext
import com.fserver.files.upload.FileId
import com.fserver.files.upload.FileRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

/** What one peer may have open at once, and what happens to what it abandoned. */
class SessionContextTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val fs = InMemoryFileSystem()
    private val progress = SyncProgressReporter(MutableTimeProvider())
    private val context = SessionContext(scope)
    private val source = sourceEntry(id = "source-1")

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
    fun `re-opening the same file drops what the first attempt had written`() = runTest {
        val first = open("file-1", path = "photo.jpg")
        first.offer(chunk("file-1", "half".toByteArray()))
        first.await()

        open("file-1", path = "photo.jpg")

        assertTrue(fs.deleted.contains("photo.jpg"))
        assertEquals(1, context.uploads.size)
    }

    @Test
    fun `an upload whose sender went quiet is pruned with its partial file`() = runTest {
        val stale = open("file-1", path = "photo.jpg")
        stale.offer(chunk("file-1", "half".toByteArray()))
        stale.await()

        context.pruneStaleUploads(TestEpoch + SessionContext.UploadTimeout + 1.minutes)

        assertTrue(context.uploads.isEmpty())
        assertNull(fs.bytesAt("photo.jpg"))
    }

    @Test
    fun `an upload still inside its timeout is left alone`() = runTest {
        open("file-1")

        context.pruneStaleUploads(TestEpoch + SessionContext.UploadTimeout - 1.minutes)

        assertEquals(1, context.uploads.size)
    }

    @Test
    fun `a session that ends takes every open upload down with it`() = runTest {
        val upload = open("file-1", path = "photo.jpg")
        upload.offer(chunk("file-1", "half".toByteArray()))
        upload.await()

        context.abandonAll()

        assertTrue(context.uploads.isEmpty())
        assertNull(fs.bytesAt("photo.jpg"))
    }

    private suspend fun open(fileId: String, path: String = "$fileId.bin"): UploadContext =
        context.start(
            source = source,
            file = FileRecord(
                id = FileId(fileId),
                path = path,
                locator = null,
                state = FileRecord.State.Present(),
                content = null,
                metadata = FileRecord.Metadata(
                    size = 0,
                    lastModified = TestEpoch,
                    revision = null,
                ),
            ),
            fs = fs,
            startedAt = TestEpoch,
            progress = progress,
        )

    private fun chunk(fileId: String, bytes: ByteArray) = FileServerMessages.UploadChunk(
        sourceId = source.id,
        fileId = fileId,
        offset = 0,
        bytes = bytes,
    )
}
