package com.fserver.core.oneshot.impl

import android.content.ContextWrapper
import com.fserver.common.exception.FileSystemException
import com.fserver.common.model.FileSize
import com.fserver.common.task.progressTask
import com.fserver.core.files.SourceLocation
import com.fserver.core.oneshot.model.OneShotTransfer
import com.fserver.files.FilesNode
import com.fserver.files.fs.FileSystemSource
import com.fserver.files.fs.FsFile
import android.os.ParcelFileDescriptor
import com.fserver.files.fs.FsReader
import com.fserver.files.fs.FsWriter
import com.fserver.files.fs.ReadableFileSystem
import com.fserver.files.fs.ReadableSource
import com.fserver.files.fs.scan.FoundFile
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.time.Instant

/** Shared files are copied while their grant lasts, and the copies go with their transfer. */
class OneShotOutboxTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val node = mockk<FilesNode>()
    private val outbox = OneShotOutbox(node)

    /** What the sharing app handed over, by uri. */
    private val shared = mutableMapOf<String, SharedFile>()

    @Before
    fun setUp() {
        // A plain directory stands in for the internal bucket.
        val bucket = FilesNode.create(ContextWrapper(null), stagingDir = temp.newFolder("outbox")).openStaging()
        every { node.openSource(any<FileSystemSource>()) } returns bucket

        every { node.openSource(any<ReadableSource>()) } answers {
            val uris = firstArg<ReadableSource.Shared>().uris
            mockk<ReadableFileSystem> {
                every { scan() } returns progressTask {
                    uris.map { FoundFile(path = shared.getValue(it).name, locator = it, size = FileSize(0), lastModified = Instant.DISTANT_PAST) }
                }
                coEvery { openFile(any()) } answers { shared[firstArg()] }
            }
        }
    }

    @Test
    fun `every shared file is copied whole under its name`() = runTest {
        share("content://a/1", "photo.jpg", Content)
        share("content://a/2", "notes.txt", "hi".toByteArray())

        val files = outbox.fill(TransferId, listOf("content://a/1", "content://a/2"))

        assertEquals(listOf("photo.jpg", "notes.txt"), files.map { it.name })
        assertEquals(listOf(Content.size.toLong(), 2L), files.map { it.size })
        assertArrayEquals(Content, copyOf(files.first().locator!!))
    }

    @Test
    fun `a file unreadable halfway leaves no copies behind`() = runTest {
        share("content://a/1", "photo.jpg", Content)
        share("content://a/2", "gone.jpg", Content, readable = false)

        assertThrows(FileSystemException.InvalidPath::class.java) {
            runBlocking { outbox.fill(TransferId, listOf("content://a/1", "content://a/2")) }
        }
        assertTrue(copies().isEmpty())
    }

    @Test
    fun `releasing a transfer drops its copies only`() = runTest {
        share("content://a/1", "photo.jpg", Content)
        outbox.fill(TransferId, listOf("content://a/1"))
        outbox.fill("other", listOf("content://a/1"))

        outbox.release(transfer(TransferId, OneShotOutbox.Location))

        assertEquals(listOf("other"), copies().map { OneShotOutbox.transferIdOf(it) })
    }

    @Test
    fun `a transfer sent from elsewhere has nothing here to release`() = runTest {
        share("content://a/1", "photo.jpg", Content)
        outbox.fill(TransferId, listOf("content://a/1"))

        outbox.release(transfer(TransferId, SourceLocation.Media))

        assertEquals(1, copies().size)
    }

    private fun share(uri: String, name: String, content: ByteArray, readable: Boolean = true) {
        shared[uri] = SharedFile(uri, name, content, readable)
    }

    private suspend fun copies(): List<String> =
        node.openSource(FileSystemSource.Internal("any")).scan().result().getOrThrow().map { it.path }

    private suspend fun copyOf(locator: String): ByteArray =
        node.openSource(FileSystemSource.Internal("any")).openFile(locator)!!.read().use { it.readBytes() }

    private fun transfer(id: String, origin: SourceLocation.Persistable) = OneShotTransfer(
        id = id,
        peer = OneShotTransfer.Peer("device-peer", "Peer"),
        direction = OneShotTransfer.Direction.Outgoing(origin),
        status = OneShotTransfer.Status.Completed,
        files = emptyList(),
        createdAt = Instant.DISTANT_PAST,
    )

    private class SharedFile(
        override val locator: String,
        val name: String,
        private val content: ByteArray,
        private val readable: Boolean,
    ) : FsFile {
        override suspend fun read(): InputStream =
            if (readable) ByteArrayInputStream(content) else throw FileSystemException.InvalidPath(locator)

        override suspend fun openReader(): FsReader = throw UnsupportedOperationException()

        override suspend fun openDescriptor(): ParcelFileDescriptor = throw UnsupportedOperationException()
        override suspend fun openWriter(): FsWriter = throw UnsupportedOperationException()
        override suspend fun rename(newName: String, deleteOldOnConflict: Boolean): FsFile = this
        override suspend fun delete(): Boolean = false
        override suspend fun settleLastModified(time: Instant): Instant = time
    }

    private companion object {
        const val TransferId = "transfer-1"

        /** Bigger than one copy buffer. */
        val Content = ByteArray(600_000) { (it % 251).toByte() }
    }
}
