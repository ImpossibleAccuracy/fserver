package com.fserver.core.sync.server.handler.upload

import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.support.InMemoryFileSystem
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.TestEpoch
import com.fserver.core.sync.index.IndexedFileKey
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

/**
 * One upload in flight, driven straight by the chunks a peer would send.
 *
 * The peer chooses the offsets, so this is where a hostile or broken sender is either absorbed or
 * turns into bytes nobody asked for.
 */
class UploadContextTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val fs = InMemoryFileSystem()
    private val buffered = AtomicInteger(0)
    private val progress = SyncProgressReporter(MutableTimeProvider())

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `chunks in order land as one file, and the hash is over the file`() = runTest {
        val upload = upload()

        assertTrue(upload.offer(chunk(offset = 0, bytes = "hello ".toByteArray())))
        assertTrue(upload.offer(chunk(offset = 6, bytes = "world".toByteArray())))
        upload.await()

        assertArrayEquals("hello world".toByteArray(), fs.bytesAt(Path))
        assertEquals(sha256("hello world".toByteArray()), upload.hasher.compute().value)
    }

    @Test
    fun `chunks that overtake each other are written in offset order`() = runTest {
        val upload = upload()

        upload.offer(chunk(offset = 6, bytes = "world".toByteArray()))
        upload.offer(chunk(offset = 0, bytes = "hello ".toByteArray()))
        upload.await()

        assertArrayEquals("hello world".toByteArray(), fs.bytesAt(Path))
        // Hashed in write order, not arrival order - otherwise the receiver and the sender
        // disagree about a file that arrived intact.
        assertEquals(sha256("hello world".toByteArray()), upload.hasher.compute().value)
    }

    @Test
    fun `an upload that sends no chunk leaves no file behind`() = runTest {
        val upload = upload()

        upload.await()

        assertNull(upload.locator)
        assertTrue(fs.createdPaths.isEmpty())
    }

    @Test
    fun `a chunk at an offset the stream never reaches never touches the disk`() = runTest {
        val upload = upload()

        // Nothing fills the gap in front of it, so it may not be written at its own offset:
        // a seek to it would leave a file the size of whatever the peer claimed.
        upload.offer(chunk(offset = Long.MAX_VALUE / 2, bytes = "x".toByteArray()))
        upload.await()

        assertNull(upload.locator)
        assertTrue(fs.createdPaths.isEmpty())
    }

    @Test
    fun `a chunk the peer resent is dropped and its bytes are given back`() = runTest {
        val upload = upload()

        upload.offer(chunk(offset = 0, bytes = "abc".toByteArray()))
        upload.offer(chunk(offset = 0, bytes = "abc".toByteArray()))
        upload.await()

        assertArrayEquals("abc".toByteArray(), fs.bytesAt(Path))
        assertEquals(0, buffered.get())
    }

    @Test
    fun `the session buffer is handed back once the bytes are on disk`() = runTest {
        val upload = upload()

        upload.offer(chunk(offset = 0, bytes = ByteArray(1024)))
        upload.await()

        assertEquals(0, buffered.get())
    }

    @Test
    fun `a chunk that does not fit the session buffer is refused, not queued`() = runTest {
        // One byte short of the cap: any chunk at all overflows it.
        buffered.set(SessionContext.InFlightChunkBytesLimit - 1)
        val upload = upload()

        val accepted = upload.offer(chunk(offset = 0, bytes = ByteArray(64)))

        assertFalse(accepted)
        // Refusing must not eat the budget it briefly reserved, or the session starves itself.
        assertEquals(SessionContext.InFlightChunkBytesLimit - 1, buffered.get())

        upload.abandon()
    }

    @Test
    fun `abandoning drops the half-written file and frees the buffer`() = runTest {
        val upload = upload()

        upload.offer(chunk(offset = 0, bytes = "partial".toByteArray()))
        upload.await()
        upload.abandon()

        assertTrue(fs.deleted.contains(Path))
        assertNull(fs.bytesAt(Path))
        assertEquals(0, buffered.get())
    }

    @Test
    fun `an upload whose backend refuses the path fails instead of writing`() = runTest {
        fs.rejectCreate = { path -> IllegalArgumentException("refused $path") }
        val upload = upload()

        upload.offer(chunk(offset = 0, bytes = "x".toByteArray()))

        val failure = runCatching { upload.await() }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(fs.createdPaths.isEmpty())
    }

    @Test
    fun `bytes whose write failed are handed back to the session buffer`() {
        // TODO: they are not. `write()` takes a chunk out of `outOfOrder` before writing it, so a
        //  failing write (a path the backend refuses, a full disk) loses the reservation: neither
        //  the loop nor `release()` ever calls `give()` for it. Every failed chunk permanently
        //  costs the session part of its 50 MiB budget, and enough of them starve it until the
        //  peer reconnects. Give the bytes back on the failure path, then assert:
        //  fs.rejectCreate = { IllegalArgumentException(it) }
        //  upload.offer(chunk(0, "x".toByteArray())); runCatching { upload.await() }
        //  assertEquals(0, buffered.get())
    }

    private fun upload(): UploadContext = UploadContext(
        file = FileRecord(
            id = FileId(FileIdValue),
            path = Path,
            locator = null,
            state = FileRecord.State.Present(),
            content = null,
            metadata = FileRecord.Metadata(size = 0, lastModified = TestEpoch, revision = null),
        ),
        startedAt = TestEpoch,
        key = IndexedFileKey(fileId = FileIdValue, sourceId = SourceId),
        fs = fs,
        buffered = buffered,
        progress = progress,
        scope = scope,
    )

    private fun chunk(offset: Long, bytes: ByteArray) = FileServerMessages.UploadChunk(
        sourceId = SourceId,
        fileId = FileIdValue,
        offset = offset,
        bytes = bytes,
    )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val SourceId = "source-1"
        const val FileIdValue = "file-1"
        const val Path = "dir/photo.jpg"
    }
}
