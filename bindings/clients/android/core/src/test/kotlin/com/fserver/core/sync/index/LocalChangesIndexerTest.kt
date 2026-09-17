package com.fserver.core.sync.index

import android.content.ContextWrapper
import com.fserver.core.files.SourceLocation
import com.fserver.core.support.FakeRequirementsChecker
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.sourceEntry
import com.fserver.core.sync.model.SourceEntry
import com.fserver.files.FilesNode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

/**
 * The local half of a pass: what the disk holds, turned into the rows a plan is made from.
 *
 * The revision counter is what a strategy reads to tell "I wrote this" from "the peer did", so a
 * counter that advances when it should not is a conflict the user never made.
 */
class LocalChangesIndexerTest {

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private val clock = MutableTimeProvider()
    private val storage = FakeStorage(localDeviceId = LocalId, clock = clock)
    private val node = FilesNode.create(ContextWrapper(null))
    private val indexer = LocalChangesIndexer(storage, node, FakeRequirementsChecker(), clock)

    private lateinit var root: File
    private lateinit var source: SourceEntry

    @Before
    fun setUp() {
        root = temp.newFolder("source-root")
        source = sourceEntry(id = SourceId, location = SourceLocation.Directory(root.absolutePath))
    }

    @Test
    fun `a file found for the first time is indexed as ours, at the first revision`() =
        runTest {
            write("photo.jpg", "one")

            val indexed = indexer.refresh(source).single()

            assertEquals("photo.jpg", indexed.path)
            assertEquals(LocalId, indexed.revision?.originDevice)
            assertEquals(1L, indexed.revision?.counter)
            assertTrue(indexed.state is LocalIndexedFile.State.Present)
            assertNull(indexed.hash)
        }

    @Test
    fun `a file nothing touched keeps the revision it had`() = runTest {
        write("photo.jpg", "one")
        indexer.refresh(source)

        val second = indexer.refresh(source).single()

        assertEquals(1L, second.revision?.counter)
    }

    @Test
    fun `an edit here advances our own counter`() = runTest {
        val file = write("photo.jpg", "one")
        indexer.refresh(source)

        write("photo.jpg", "one plus more")
        file.setLastModified(file.lastModified() + 60_000)

        assertEquals(2L, indexer.refresh(source).single().revision?.counter)
    }

    @Test
    fun `taking over a file the peer wrote restarts the count under our own id`() = runTest {
        val file = write("photo.jpg", "from the peer")
        val adopted = indexer.refresh(source).single().copy(
            revision = LocalIndexedFile.Revision(originDevice = PeerId, counter = 9),
        )
        storage.index.markProcessed(listOf(adopted))

        write("photo.jpg", "edited here")
        file.setLastModified(file.lastModified() + 60_000)

        val reindexed = indexer.refresh(source).single()

        // Counters only ever compare within one originDevice, so continuing the peer's count
        // would make our write look like its ninth.
        assertEquals(LocalId, reindexed.revision?.originDevice)
        assertEquals(1L, reindexed.revision?.counter)
    }

    @Test
    fun `a file the user removed becomes a tombstone rather than vanishing from the index`() =
        runTest {
            val file = write("photo.jpg", "one")
            indexer.refresh(source)

            file.delete()
            val indexed = indexer.refresh(source).single()

            // The row has to outlive the file: it is the only thing that tells the peer the file
            // was deleted rather than never sent.
            assertTrue(indexed.state is LocalIndexedFile.State.Deleted)
        }

    @Test
    fun `a file that comes back is present again`() = runTest {
        val file = write("photo.jpg", "one")
        indexer.refresh(source)
        file.delete()
        indexer.refresh(source)

        write("photo.jpg", "restored")
        val indexed = indexer.refresh(source).single()

        assertTrue(indexed.state is LocalIndexedFile.State.Present)
    }

    @Test
    fun `two files with the same name in different directories stay apart`() = runTest {
        write("a/photo.jpg", "one")
        write("b/photo.jpg", "two")

        val indexed = indexer.refresh(source)

        assertEquals(setOf("a/photo.jpg", "b/photo.jpg"), indexed.map { it.path }.toSet())
        assertEquals(2, indexed.map { it.fileId }.toSet().size)
    }

    @Test
    fun `hashing a file records its content hash against the row`() = runTest {
        write("photo.jpg", "hash me")
        val indexed = indexer.refresh(source).single()

        indexer.hashFile(source, indexed)

        val key = IndexedFileKey(fileId = indexed.fileId, sourceId = SourceId)
        assertEquals(sha256("hash me".toByteArray()), storage.index.findFile(key)?.hash?.value)
    }

    private fun write(path: String, contents: String): File =
        File(root, path).apply {
            parentFile?.mkdirs()
            writeText(contents)
        }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val SourceId = "source-1"
        const val LocalId = "device-local"
        const val PeerId = "device-peer"
    }
}
