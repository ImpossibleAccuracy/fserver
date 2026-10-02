package com.fserver.core.crypto.internal

import android.content.ContextWrapper
import com.fserver.common.exception.FileSystemException
import com.fserver.common.utils.SourcePaths
import com.fserver.core.crypto.model.AtRest
import com.fserver.core.crypto.model.EncryptionPolicy
import com.fserver.core.crypto.model.requireEncryptable
import com.fserver.core.crypto.model.supportsEncryption
import com.fserver.core.files.SourceLocation
import com.fserver.core.files.access.LocalFileEditor
import com.fserver.core.files.util.FileHasher
import com.fserver.core.support.FakeRequirementsChecker
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.LocalIndex
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.TestEpoch
import com.fserver.core.support.sourceEntry
import com.fserver.core.support.sourceFiles
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import com.fserver.files.FilesNode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.random.Random

/** A source under `Required` holds only ciphertext on disk, and everything above it sees plaintext. */
class EncryptedFileSystemTest {

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private val clock = MutableTimeProvider(TestEpoch)
    private val storage = FakeStorage(clock = clock)

    private lateinit var node: FilesNode
    private lateinit var root: File
    private lateinit var index: LocalIndex
    private lateinit var editor: LocalFileEditor

    @Before
    fun setUp() {
        node = FilesNode.create(ContextWrapper(null), stagingDir = temp.newFolder("staging"))
        root = temp.newFolder("source-root")
        index = LocalIndex(storage, node, clock)
        editor = LocalFileEditor(storage, sourceFiles(storage, node), index.writer, FakeRequirementsChecker(), clock)
    }

    @Test
    fun `a file written under Required is ciphertext on disk and plaintext above it`() = runTest {
        register(EncryptionPolicy.Required())

        val file = editor.create(SourceId, "docs/note.txt").write { it.write(0, Content) }

        val disk = File(root, "docs/note.txt").readBytes()
        assertTrue(disk.startsWithMagic())
        assertFalse(disk.contains(Content))
        assertArrayEquals(Content, file.read().use { it.readBytes() })
        assertEquals(Content.size.toLong(), file.size.bytes)
        assertTrue(row("docs/note.txt").atRest is AtRest.Sealed)
        assertNull(file.openDescriptor())
    }

    @Test
    fun `a scan reports plaintext sizes and hashes plaintext`() = runTest {
        register(EncryptionPolicy.Required())
        editor.create(SourceId, "a.bin").write { it.write(0, Content) }

        // Forgotten, so the scan has to read the header rather than trust the row.
        storage.index.clearProcessed(SourceId)
        val rows = index.indexer.refresh(source())
        val row = rows.single()
        index.hasher.hashFile(source(), row)

        assertEquals(Content.size.toLong(), row.size.bytes)
        assertTrue(row.atRest is AtRest.Sealed)
        assertEquals(FileHasher().apply { write(Content, Content.size) }.compute(), row("a.bin").hash)
    }

    @Test
    fun `plaintext placed under Required is sealed on the way in`() = runTest {
        register(EncryptionPolicy.Required())
        val staged = staged(Content)
        val fs = sourceFiles(storage, node).open(source())

        val placed = fs.place(staged, "in/photo.jpg")

        assertTrue(File(root, "in/photo.jpg").readBytes().startsWithMagic())
        assertTrue(placed.atRest is AtRest.Sealed)
        assertArrayEquals(Content, placed.read().use { it.readBytes() })
        assertFalse(File(staged.locator).exists())
        assertEquals(listOf("photo.jpg"), File(root, "in").list()!!.toList())
    }

    @Test
    fun `under Off nothing is sealed`() = runTest {
        register(EncryptionPolicy.Off)
        val placed = sourceFiles(storage, node).open(source()).place(staged(Content), "a.bin")

        assertArrayEquals(Content, File(root, "a.bin").readBytes())
        assertEquals(AtRest.Plain, placed.atRest)
    }

    @Test
    fun `a file sealed earlier stays readable once the policy is Off`() = runTest {
        register(EncryptionPolicy.Required())
        editor.create(SourceId, "a.bin").write { it.write(0, Content) }
        register(EncryptionPolicy.Off)

        val file = editor.find(SourceId, SourcePaths.fileId("a.bin"))!!
        assertArrayEquals(Content, file.read().use { it.readBytes() })
    }

    @Test
    fun `plaintext the index never saw sealed is pending, and read as it is`() = runTest {
        register(EncryptionPolicy.Required())
        File(root, "dropped.txt").writeBytes(Content)

        val row = index.indexer.refresh(source()).single()

        assertEquals(AtRest.Plain, row.atRest)
        assertEquals(Content.size.toLong(), row.size.bytes)
        assertArrayEquals(Content, editor.find(SourceId, row.fileId)!!.read().use { it.readBytes() })
    }

    @Test
    fun `plaintext swapped in for a sealed file is refused and does not reach the index`() = runTest {
        register(EncryptionPolicy.Required())
        editor.create(SourceId, "a.bin").write { it.write(0, Content) }
        val before = row("a.bin")

        File(root, "a.bin").writeBytes("attacker".toByteArray())
        index.indexer.refresh(source())

        assertEquals(before.size, row("a.bin").size)
        assertEquals(before.version, row("a.bin").version)
        assertThrows<FileSystemException.Corrupted> {
            editor.find(SourceId, before.fileId)!!.read().use { it.readBytes() }
        }
    }

    @Test
    fun `new files are sealed by the cipher the source names`() = runTest {
        register(EncryptionPolicy.Required(AltCipher.id))
        val fs = SourceFileSystems(node, storage, SealedFiles(listOf(AltCipher), storage.storageKeys)).open(source())

        val placed = fs.place(staged(Content), "a.bin")

        assertEquals(AltCipher.id, (placed.atRest as AtRest.Sealed).cipherId)
        assertArrayEquals(Content, placed.read().use { it.readBytes() })
    }

    @Test
    fun `shared storage never takes Required`() {
        for (location in listOf(SourceLocation.Media, SourceLocation.Downloads("FServer"))) {
            assertFalse(location.supportsEncryption)
            assertThrows<IllegalArgumentException> { requireEncryptable(location, EncryptionPolicy.Required()) }
        }
        assertTrue(SourceLocation.Internal("bucket").supportsEncryption)
    }

    private var policy: EncryptionPolicy = EncryptionPolicy.Off

    private suspend fun register(policy: EncryptionPolicy) {
        this.policy = policy
        storage.sources.upsert(source())
    }

    private fun source(): SourceEntry {
        val base = sourceEntry(
            id = SourceId,
            location = SourceLocation.Directory(root.absolutePath),
            syncMode = SyncMode.Mirror(SyncMode.Mirror.ConflictResolution.LastWriteWins),
            role = SourceEntry.Role.Initiator,
        )
        return base.copy(preferences = base.preferences.copy(encryption = policy))
    }

    private suspend fun staged(content: ByteArray) = node.openStaging().createFile("upload-${Random.nextInt()}").also { file ->
        file.openWriter().use { it.write(0, content) }
    }

    private suspend fun row(path: String) =
        storage.index.findFile(IndexedFileKey(fileId = SourcePaths.fileId(path), sourceId = SourceId))!!

    private fun ByteArray.startsWithMagic() = size >= 4 && String(copyOf(4)) == "FSEC"

    private fun ByteArray.contains(part: ByteArray) =
        (0..size - part.size).any { start -> part.indices.all { this[start + it] == part[it] } }

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return
            throw AssertionError("Expected ${T::class.simpleName}, got $e", e)
        }
        throw AssertionError("Expected ${T::class.simpleName}, nothing thrown")
    }

    private companion object {
        const val SourceId = "source-1"
        val Content = "the quick brown fox jumps over the lazy dog".repeat(4000).toByteArray()
    }
}
