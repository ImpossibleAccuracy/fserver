package com.fserver.core.sync.index

import android.content.ContextWrapper
import com.fserver.common.model.ContentHash
import com.fserver.common.utils.SourcePaths
import com.fserver.core.files.SourceLocation
import com.fserver.core.support.FakeRequirementsChecker
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.LocalIndex
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.sourceEntry
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.progress.IndexingProgress
import com.fserver.core.sync.progress.SourcePass
import com.fserver.core.sync.progress.impl.SyncProgressReporter
import com.fserver.core.sync.version.HlcTimestamp
import com.fserver.core.sync.version.HybridLogicalClock
import com.fserver.core.sync.version.VersionVector
import com.fserver.files.FilesNode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The local half of a pass: what the disk holds, turned into the rows a plan is made from.
 *
 * The version vector is what a strategy reads to order this device's edits against the peer's, so
 * one that advances when it should not is a conflict the user never made.
 */
class LocalChangesIndexerTest {

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private val clock = MutableTimeProvider()
    private val storage = FakeStorage(localDeviceId = LocalId, clock = clock)
    private val node = FilesNode.create(ContextWrapper(null))
    private val progress = SyncProgressReporter(clock)
    private val index = LocalIndex(storage, node, clock, progress)
    private val indexer = index.indexer

    private lateinit var root: File
    private lateinit var source: SourceEntry

    @Before
    fun setUp() {
        root = temp.newFolder("source-root")
        source = sourceEntry(id = SourceId, location = SourceLocation.Directory(root.absolutePath))
    }

    @Test
    fun `a file found for the first time is indexed as ours, at the first version`() =
        runTest {
            write("photo.jpg", "one")

            val indexed = indexer.refresh(source).single()

            assertEquals("photo.jpg", indexed.path)
            assertEquals(LocalId, indexed.version?.originDevice)
            assertEquals(VersionVector(mapOf(LocalId to 1L)), indexed.version?.vector)
            assertTrue(indexed.state is LocalIndexedFile.State.Present)
            assertNull(indexed.hash)
        }

    @Test
    fun `a file nothing touched keeps the version it had`() = runTest {
        write("photo.jpg", "one")
        val first = indexer.refresh(source).single()

        val second = indexer.refresh(source).single()

        assertEquals(first.version, second.version)
    }

    @Test
    fun `an edit here advances our own counter and the clock`() = runTest {
        val file = write("photo.jpg", "one")
        val before = indexer.refresh(source).single().version!!

        write("photo.jpg", "one plus more")
        file.setLastModified(file.lastModified() + 60_000)
        val after = indexer.refresh(source).single().version!!

        assertEquals(VersionVector(mapOf(LocalId to 2L)), after.vector)
        assertTrue(after.hlc > before.hlc)
    }

    @Test
    fun `editing a file the peer wrote keeps the peer's edits in the vector`() = runTest {
        val file = write("photo.jpg", "from the peer")
        val adopted = indexer.refresh(source).single().copy(
            version = LocalIndexedFile.Version(
                vector = VersionVector(mapOf(PeerId to 9L)),
                hlc = HlcTimestamp.Zero,
                originDevice = PeerId,
            ),
        )
        storage.index.markProcessed(listOf(adopted))

        write("photo.jpg", "edited here")
        file.setLastModified(file.lastModified() + 60_000)

        val reindexed = indexer.refresh(source).single()

        // Dropping the peer's nine edits would make ours look concurrent with them.
        assertEquals(LocalId, reindexed.version?.originDevice)
        assertEquals(VersionVector(mapOf(PeerId to 9L, LocalId to 1L)), reindexed.version?.vector)
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
    fun `a deletion is a new version, so it orders against the peer's edits`() = runTest {
        val file = write("photo.jpg", "one")
        indexer.refresh(source)

        file.delete()
        val indexed = indexer.refresh(source).single()

        assertEquals(VersionVector(mapOf(LocalId to 2L)), indexed.version?.vector)
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
    fun `a touch that kept the bytes is not a new version once hashed`() = runTest {
        val file = write("photo.jpg", "one")
        val before = hashed(indexer.refresh(source).single())

        file.setLastModified(file.lastModified() + 60_000)
        val touched = indexer.refresh(source).single()
        val after = hashed(touched)

        // Offered to a plan as unknown, but kept to compare the next hash with.
        assertTrue(touched.hashStale)
        assertNull(touched.toFileRecord().content)
        assertEquals(before.version, after.version)
        assertFalse(after.hashStale)
    }

    @Test
    fun `a same-size edit waits for the hash, then becomes a new version`() = runTest {
        val file = write("photo.jpg", "one")
        val before = hashed(indexer.refresh(source).single())

        write("photo.jpg", "two")
        file.setLastModified(file.lastModified() + 60_000)
        val pending = indexer.refresh(source).single()

        assertEquals(before.version, pending.version)
        assertEquals(VersionVector(mapOf(LocalId to 2L)), hashed(pending).version?.vector)
    }

    @Test
    fun `a touch of a file never hashed is a new version, since nothing proves it unchanged`() =
        runTest {
            val file = write("photo.jpg", "one")
            indexer.refresh(source)

            file.setLastModified(file.lastModified() + 60_000)

            assertEquals(VersionVector(mapOf(LocalId to 2L)), indexer.refresh(source).single().version?.vector)
        }

    @Test
    fun `a hash of bytes that changed while hashing is dropped`() = runTest {
        val file = write("photo.jpg", "one")
        val stale = indexer.refresh(source).single()

        write("photo.jpg", "one plus more")
        file.setLastModified(file.lastModified() + 60_000)
        indexer.refresh(source)
        index.hasher.hashFile(source, stale)

        assertNull(storage.index.findFile(key(stale))?.hash)
    }

    @Test
    fun `a deletion done for the peer is recorded under the peer's version`() = runTest {
        write("photo.jpg", "one")
        val indexed = indexer.refresh(source).single()
        val peers = LocalIndexedFile.Version(
            vector = VersionVector(mapOf(LocalId to 1L, PeerId to 1L)),
            hlc = HlcTimestamp.of(5, 0),
            originDevice = PeerId,
        )

        index.writer.recordDeleted(source, key(indexed), peers)

        val row = storage.index.findFile(key(indexed))
        assertTrue(row?.state is LocalIndexedFile.State.Deleted)
        assertEquals(peers, row?.version)
    }

    @Test
    fun `a deletion with no version given is a new version of our own`() = runTest {
        write("photo.jpg", "one")
        val indexed = indexer.refresh(source).single()

        index.writer.recordDeleted(source, key(indexed), version = null)

        assertEquals(VersionVector(mapOf(LocalId to 2L)), storage.index.findFile(key(indexed))?.version?.vector)
    }

    @Test
    fun `a merged version is adopted for the content it was planned on`() = runTest {
        write("photo.jpg", "one")
        val indexed = hashed(indexer.refresh(source).single())
        val merged = LocalIndexedFile.Version(
            vector = VersionVector(mapOf(LocalId to 1L, PeerId to 1L)),
            hlc = HlcTimestamp.of(5, 0),
            originDevice = PeerId,
        )

        index.writer.adoptVersion(source, key(indexed), merged, expected = indexed.hash)

        assertEquals(merged, storage.index.findFile(key(indexed))?.version)
    }

    @Test
    fun `a merged version is refused once the content moved on`() = runTest {
        write("photo.jpg", "one")
        val indexed = hashed(indexer.refresh(source).single())
        val merged = indexed.version!!.copy(vector = VersionVector(mapOf(LocalId to 1L, PeerId to 1L)))

        val failure = runCatching {
            index.writer.adoptVersion(source, key(indexed), merged, expected = ContentHash("other", "SHA-256"))
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(indexed.version, storage.index.findFile(key(indexed))?.version)
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

        index.hasher.hashFile(source, indexed)

        val key = IndexedFileKey(fileId = indexed.fileId, sourceId = SourceId)
        assertEquals(sha256("hash me".toByteArray()), storage.index.findFile(key)?.hash?.value)
    }

    @Test
    fun `a rename of a hashed file hashes the new path, so a plan can match it with the tombstone`() = runTest {
        val file = write("photo.jpg", "one")
        val before = hashed(indexer.refresh(source).single())

        file.renameTo(File(root, "renamed.jpg"))
        val indexed = indexer.refresh(source)

        val tombstone = indexed.single { it.path == "photo.jpg" }
        val renamed = indexed.single { it.path == "renamed.jpg" }
        assertTrue(tombstone.state is LocalIndexedFile.State.Deleted)
        assertEquals(before.hash, tombstone.hash)
        assertEquals(before.hash, renamed.hash)
        // Hashing an unhashed row proves nothing new, so the file keeps its first version.
        assertEquals(VersionVector(mapOf(LocalId to 1L)), renamed.version?.vector)
    }

    @Test
    fun `a rename of a file never hashed hashes nothing`() = runTest {
        val file = write("photo.jpg", "one")
        indexer.refresh(source)

        file.renameTo(File(root, "renamed.jpg"))
        val indexed = indexer.refresh(source)

        assertNull(indexed.single { it.path == "renamed.jpg" }.hash)
        assertEquals(0, progress.indexing(SourceId).first()?.filesToHash)
    }

    @Test
    fun `a tombstone with a stale hash matches no rename`() = runTest {
        val file = write("photo.jpg", "one")
        hashed(indexer.refresh(source).single())
        file.setLastModified(file.lastModified() + 60_000)
        indexer.refresh(source)

        file.renameTo(File(root, "renamed.jpg"))
        val indexed = indexer.refresh(source)

        assertTrue(indexed.single { it.path == "photo.jpg" }.hashStale)
        assertNull(indexed.single { it.path == "renamed.jpg" }.hash)
    }

    @Test
    fun `an indexing run reports what it scanned and hashed`() = runTest {
        val file = write("photo.jpg", "one")
        hashed(indexer.refresh(source).single())
        file.renameTo(File(root, "renamed.jpg"))

        indexer.refresh(source)

        val run = progress.indexing(SourceId).first()!!
        assertEquals(IndexingProgress.Stage.Finished, run.stage)
        assertEquals(1, run.filesScanned)
        assertEquals(1, run.filesHashed)
        assertEquals(1, run.filesToHash)
    }

    @Test
    fun `a pass still indexing shows the hashing as its stage`() = runTest {
        val file = write("photo.jpg", "one")
        hashed(indexer.refresh(source).single())
        file.renameTo(File(root, "renamed.jpg"))
        progress.localPassStarted(SourceId)

        indexer.refresh(source)

        assertEquals(SourcePass.Local.Stage.Hashing, (progress.pass(SourceId).first() as SourcePass.Local).stage)
    }

    @Test
    fun `temp, lock and service files are not indexed`() = runTest {
        write("photo.jpg", "one")
        write("report.docx.tmp", "x")
        write("~\$report.docx", "x")
        write(".nomedia", "")
        write("DCIM/.thumbnails/1.jpg", "x")
        write(".trashed-1700000000-old.jpg", "x")
        write("clip.fserver-part.mp4", "x")

        assertEquals(listOf("photo.jpg"), indexer.refresh(source).map { it.path })
    }

    @Test
    fun `an indexed file that is now ignored is not turned into a tombstone`() = runTest {
        write("photo.jpg", "one")
        val photo = indexer.refresh(source).single()
        // Indexed before the rule existed.
        storage.index.markProcessed(
            listOf(photo.copy(id = "nomedia", fileId = SourcePaths.fileId(".nomedia"), path = ".nomedia")),
        )

        val indexed = indexer.refresh(source).single { it.path == ".nomedia" }

        // A tombstone would delete the peer's copy.
        assertTrue(indexed.state is LocalIndexedFile.State.Present)
    }

    @Test
    fun `an edit under an editor's lock waits for the lock to go`() = runTest {
        val file = write("report.docx", "one")
        indexer.refresh(source)

        val lock = lock("~\$port.docx")
        write("report.docx", "one plus more")
        file.setLastModified(file.lastModified() + 60_000)
        assertEquals(VersionVector(mapOf(LocalId to 1L)), indexer.refresh(source).single().version?.vector)

        lock.delete()
        assertEquals(VersionVector(mapOf(LocalId to 2L)), indexer.refresh(source).single().version?.vector)
    }

    @Test
    fun `a locked file missing mid-save is not a deletion`() = runTest {
        val file = write("report.odt", "one")
        indexer.refresh(source)

        lock(".~lock.report.odt#")
        file.delete()

        assertTrue(indexer.refresh(source).single().state is LocalIndexedFile.State.Present)
    }

    @Test
    fun `a new file under a lock is not indexed yet`() = runTest {
        write("notes.txt", "draft")
        lock(".#notes.txt")

        assertTrue(indexer.refresh(source).isEmpty())
    }

    @Test
    fun `a lock older than the limit holds nothing`() = runTest {
        write("report.docx", "one")
        lock("~\$report.docx", age = EditLocks.MaxAge + 1.minutes)

        assertEquals(listOf("report.docx"), indexer.refresh(source).map { it.path })
    }

    private suspend fun hashed(file: LocalIndexedFile): LocalIndexedFile {
        index.hasher.hashFile(source, file)
        return storage.index.findFile(key(file))!!
    }

    private fun key(file: LocalIndexedFile) = IndexedFileKey(fileId = file.fileId, sourceId = SourceId)

    private fun write(path: String, contents: String): File =
        File(root, path).apply {
            parentFile?.mkdirs()
            writeText(contents)
        }

    private fun lock(name: String, age: Duration = Duration.ZERO): File =
        write(name, "").apply { setLastModified((clock.now() - age).toEpochMilliseconds()) }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val SourceId = "source-1"
        const val LocalId = "device-local"
        const val PeerId = "device-peer"
    }
}
