package com.fserver.core.sync.server.handler.upload

import android.content.ContextWrapper
import com.fserver.common.exception.TransferException
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.FileServerMessages.Upload
import com.fserver.core.network.dictionary.dto.UploadKey
import com.fserver.core.support.LocalIndex
import com.fserver.core.support.fileDto
import com.fserver.core.support.peerIdentity
import com.fserver.core.support.sourceFiles
import com.fserver.core.sync.server.SourceAuthorizer
import com.fserver.core.sync.transfer.RequestedDownloads
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.TestEpoch
import com.fserver.core.support.sourceEntry
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.progress.impl.SyncProgressReporter
import com.fserver.core.sync.server.handler.upload.source.SourceUploadTarget
import com.fserver.files.FilesNode
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
class SessionUploadsTest {

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clock = MutableTimeProvider()
    private val storage = FakeStorage(clock = clock)
    private val progress = SyncProgressReporter(clock)
    private val source = sourceEntry(id = "source-1")

    private lateinit var stagingDir: File
    private lateinit var staging: UploadStaging
    private lateinit var uploads: SessionUploads
    private lateinit var target: SourceUploadTarget

    @Before
    fun setUp() = runBlocking {
        stagingDir = temp.newFolder("staging")
        val node = FilesNode.create(ContextWrapper(null), stagingDir = stagingDir)
        staging = UploadStaging(storage, node, clock)
        uploads = SessionUploads(scope, progress)
        target = SourceUploadTarget(
            authorizer = SourceAuthorizer(storage),
            admission = UploadAdmission(storage, RequestedDownloads()),
            indexWriter = LocalIndex(storage, node, clock).writer,
            sourceFiles = sourceFiles(storage, node),
            staging = staging,
        )
        storage.sources.upsert(source)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `a peer may not open more uploads than the session allows`() = runTest {
        repeat(SessionUploads.MaxConcurrentUploads) { open("file-$it") }

        val failure = runCatching { open("one-too-many") }.exceptionOrNull()

        assertTrue(failure is TransferException.TooManyUploadsException)
        assertEquals(SessionUploads.MaxConcurrentUploads, uploads.inFlight.size)
    }

    @Test
    fun `re-opening the same file resumes from what the first attempt wrote`() = runTest {
        val first = open("file-1")
        first.offer(chunk("file-1", "half".toByteArray()))
        first.await()

        val second = open("file-1")

        assertEquals(4, second.prefix)
        assertEquals(1, uploads.inFlight.size)
        assertEquals("half", stagedBytes("file-1"))
    }

    @Test
    fun `an upload whose sender went quiet is parked with its staged bytes`() = runTest {
        val stale = open("file-1")
        stale.offer(chunk("file-1", "half".toByteArray()))
        stale.await()

        uploads.pruneStale(TestEpoch + SessionUploads.UploadTimeout + 1.minutes)

        assertTrue(uploads.inFlight.isEmpty())
        assertEquals("half", stagedBytes("file-1"))
        assertEquals(4L, storage.uploads.find(key("file-1"))?.committedOffset)
    }

    @Test
    fun `an upload still inside its timeout is left alone`() = runTest {
        open("file-1")

        uploads.pruneStale(TestEpoch + SessionUploads.UploadTimeout - 1.minutes)

        assertEquals(1, uploads.inFlight.size)
    }

    @Test
    fun `a session that ends parks every open upload`() = runTest {
        val upload = open("file-1")
        upload.offer(chunk("file-1", "half".toByteArray()))
        upload.await()

        uploads.parkAll()

        assertTrue(uploads.inFlight.isEmpty())
        assertEquals(4L, storage.uploads.find(key("file-1"))?.committedOffset)
    }

    /** What the handler does on Init: make room, let the target open staging, start. */
    private suspend fun open(fileId: String): UploadContext {
        val key = UploadKey.Source(sourceId = source.id, fileId = fileId)
        val init = Upload.Init(key, fileDto(id = fileId, sourceId = source.id, path = "$fileId.bin", size = 100))

        uploads.reserve(key)
        val staged = target.open(peerIdentity(source.deviceId), init, uploads) as UploadTarget.Opening.Staged

        return uploads.start(key, staged, TestEpoch)
    }

    private fun key(fileId: String) = IndexedFileKey(fileId = fileId, sourceId = source.id)

    private fun stagedBytes(fileId: String): String =
        File(stagingDir, "${source.id}/$fileId/data").readText()

    private fun chunk(fileId: String, bytes: ByteArray) = FileServerMessages.UploadChunk(
        key = UploadKey.Source(sourceId = source.id, fileId = fileId),
        offset = 0,
        bytes = bytes,
    )
}
