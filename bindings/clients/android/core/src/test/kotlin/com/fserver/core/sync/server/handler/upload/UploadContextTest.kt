package com.fserver.core.sync.server.handler.upload

import com.fserver.common.exception.TransferException
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.support.InMemoryFileSystem
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.TestEpoch
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.progress.impl.SyncProgressReporter
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
    fun `chunks in order land as one file`() = runTest {
        val upload = upload(size = 11)

        assertTrue(upload.offer(chunk(offset = 0, bytes = "hello ".toByteArray())))
        assertTrue(upload.offer(chunk(offset = 6, bytes = "world".toByteArray())))
        upload.await()

        assertArrayEquals("hello world".toByteArray(), fs.bytesAt(Staging))
        assertTrue(upload.isWhole)
        assertEquals(sha256("hello world"), upload.hash().value)
    }

    @Test
    fun `chunks that overtake each other are written at their own offsets`() = runTest {
        val upload = upload(size = 11)

        upload.offer(chunk(offset = 6, bytes = "world".toByteArray()))
        upload.await()

        // Written, but the gap in front of it means nothing is whole yet.
        assertEquals(0, upload.prefix)

        val resumed = upload(size = 11)
        resumed.offer(chunk(offset = 6, bytes = "world".toByteArray()))
        resumed.offer(chunk(offset = 0, bytes = "hello ".toByteArray()))
        resumed.await()

        assertArrayEquals("hello world".toByteArray(), fs.bytesAt(Staging))
        assertEquals(11, resumed.prefix)
        // Hashed in offset order, the early chunk read back from disk once the gap was filled.
        assertEquals(sha256("hello world"), resumed.hash().value)
    }

    @Test
    fun `a chunk past the size Init declared is refused and never touches the disk`() = runTest {
        val upload = upload(size = 4)

        // A seek to it would leave a file the size of whatever the peer claimed.
        upload.offer(chunk(offset = Long.MAX_VALUE / 2, bytes = "x".toByteArray()))

        val failure = runCatching { upload.await() }.exceptionOrNull()

        assertTrue(failure is TransferException.ChunkOutOfBoundsException)
        assertEquals(0, fs.bytesAt(Staging)?.size)
    }

    @Test
    fun `a chunk the peer resent is dropped and its bytes are given back`() = runTest {
        val upload = upload(size = 3)

        upload.offer(chunk(offset = 0, bytes = "abc".toByteArray()))
        upload.offer(chunk(offset = 0, bytes = "abc".toByteArray()))
        upload.await()

        assertArrayEquals("abc".toByteArray(), fs.bytesAt(Staging))
        assertEquals(0, buffered.get())
    }

    @Test
    fun `bytes an earlier attempt committed are not written again`() = runTest {
        fs.createFile(Staging).openWriter().use { it.write(offset = 0, bytes = "abc".toByteArray()) }
        val upload = upload(size = 6, committed = 3, create = false)

        upload.offer(chunk(offset = 0, bytes = "XYZ".toByteArray()))
        upload.offer(chunk(offset = 3, bytes = "def".toByteArray()))
        upload.await()

        assertArrayEquals("abcdef".toByteArray(), fs.bytesAt(Staging))
        assertTrue(upload.isWhole)
        // The committed bytes are hashed from disk: the hasher of the earlier attempt is gone.
        assertEquals(sha256("abcdef"), upload.hash().value)
    }

    @Test
    fun `a resume with every byte already committed still has the whole hash`() = runTest {
        fs.createFile(Staging).openWriter().use { it.write(offset = 0, bytes = "abc".toByteArray()) }
        val upload = upload(size = 3, committed = 3, create = false)

        upload.await()

        assertEquals(sha256("abc"), upload.hash().value)
    }

    @Test
    fun `the session buffer is handed back once the bytes are on disk`() = runTest {
        val upload = upload(size = 1024)

        upload.offer(chunk(offset = 0, bytes = ByteArray(1024)))
        upload.await()

        assertEquals(0, buffered.get())
    }

    @Test
    fun `a chunk that does not fit the session buffer is refused, not queued`() = runTest {
        // One byte short of the cap: any chunk at all overflows it.
        buffered.set(SessionUploads.InFlightChunkBytesLimit - 1)
        val upload = upload(size = 64)

        val accepted = upload.offer(chunk(offset = 0, bytes = ByteArray(64)))

        assertFalse(accepted)
        // Refusing must not eat the budget it briefly reserved, or the session starves itself.
        assertEquals(SessionUploads.InFlightChunkBytesLimit - 1, buffered.get())

        upload.close()
    }

    @Test
    fun `a flush syncs the descriptor and reports what is safe to record`() = runTest {
        val upload = upload(size = 100)

        upload.offer(chunk(offset = 0, bytes = "0123".toByteArray()))
        upload.await()

        assertEquals(4, upload.flush())
        assertEquals(listOf(Staging), fs.synced)
    }

    @Test
    fun `closing keeps the staged bytes and frees the buffer`() = runTest {
        val upload = upload(size = 100)

        upload.offer(chunk(offset = 0, bytes = "partial".toByteArray()))
        upload.await()
        upload.close()

        assertArrayEquals("partial".toByteArray(), fs.bytesAt(Staging))
        assertTrue(fs.deleted.isEmpty())
        assertEquals(0, buffered.get())
    }

    @Test
    fun `bytes whose write failed are handed back to the session buffer`() = runTest {
        val upload = upload(size = 1)
        fs.openFile(Staging)!!.delete()

        upload.offer(chunk(offset = 0, bytes = "x".toByteArray()))

        assertTrue(runCatching { upload.await() }.isFailure)
        assertEquals(0, buffered.get())
    }

    private suspend fun upload(
        size: Long,
        committed: Long = 0,
        create: Boolean = fs.bytesAt(Staging) == null,
    ): UploadContext {
        val staged = if (create) fs.createFile(Staging) else fs.openFile(Staging)!!
        return UploadContext(
        file = FileRecord(
            id = FileId(FileIdValue),
            path = "dir/photo.jpg",
            locator = null,
            state = FileRecord.State.Present(),
            content = null,
            metadata = FileRecord.Metadata(size = size, lastModified = TestEpoch, version = null),
        ),
        key = IndexedFileKey(fileId = FileIdValue, sourceId = SourceId),
        fs = fs,
        staging = staged,
        out = staged.openWriter(),
        committed = committed,
        startedAt = TestEpoch,
        buffered = buffered,
        progress = progress,
        scope = scope,
    )
    }

    private fun chunk(offset: Long, bytes: ByteArray) = FileServerMessages.UploadChunk(
        sourceId = SourceId,
        fileId = FileIdValue,
        offset = offset,
        bytes = bytes,
    )

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val SourceId = "source-1"
        const val FileIdValue = "file-1"
        const val Staging = "staging/data"
    }
}
