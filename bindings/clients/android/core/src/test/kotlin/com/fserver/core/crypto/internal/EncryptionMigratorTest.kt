package com.fserver.core.crypto.internal

import android.content.ContextWrapper
import com.fserver.common.model.FileSize
import com.fserver.common.utils.SourcePaths
import com.fserver.core.crypto.model.AtRest
import com.fserver.core.crypto.model.EncryptionPolicy
import com.fserver.core.files.SourceLocation
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.LocalIndex
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.TestEpoch
import com.fserver.core.support.sourceEntry
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.model.SourceEntry
import com.fserver.files.FilesNode
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Migration changes how bytes sit on disk and nothing else: content, version and mtime stay. */
class EncryptionMigratorTest {

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private val clock = MutableTimeProvider(TestEpoch)
    private val storage = FakeStorage(clock = clock)
    private val sealedFiles = SealedFiles(listOf(AltCipher), storage.storageKeys)

    private lateinit var node: FilesNode
    private lateinit var root: File
    private lateinit var index: LocalIndex
    private lateinit var migrator: EncryptionMigrator

    @Before
    fun setUp() {
        node = FilesNode.create(ContextWrapper(null), stagingDir = temp.newFolder("staging"))
        root = temp.newFolder("source-root")
        index = LocalIndex(storage, node, clock)
        val sourceFiles = SourceFileSystems(node, storage, sealedFiles)
        migrator = EncryptionMigrator(storage, sourceFiles, sealedFiles, index.writer, index.indexer, TestScope())
    }

    @Test
    fun `turning Required on seals what is there, and changes nothing else`() = runTest {
        register(EncryptionPolicy.Off)
        File(root, "dir/a.txt").apply { parentFile!!.mkdirs(); writeBytes(Content) }
        index.indexer.refresh(source(EncryptionPolicy.Off))
        index.hasher.hashFile(source(EncryptionPolicy.Off), row("dir/a.txt"))
        val before = row("dir/a.txt")

        register(EncryptionPolicy.Required())
        migrator.migrate()

        val after = row("dir/a.txt")
        assertTrue(File(root, "dir/a.txt").readBytes().copyOf(4).contentEquals("FSEC".toByteArray()))
        assertTrue(after.atRest is AtRest.Sealed)
        assertEquals(before.copy(atRest = after.atRest, locator = after.locator), after)
        assertEquals(listOf("a.txt"), File(root, "dir").list()!!.toList())

        // The next scan sees the same file: no new version.
        index.indexer.refresh(source(EncryptionPolicy.Required()))
        assertEquals(after, row("dir/a.txt"))
        assertArrayEquals(Content, read("dir/a.txt"))
    }

    @Test
    fun `turning Required off opens sealed files back up`() = runTest {
        register(EncryptionPolicy.Required())
        sealedFile("a.txt")

        register(EncryptionPolicy.Off)
        migrator.migrate()

        assertArrayEquals(Content, File(root, "a.txt").readBytes())
        assertEquals(AtRest.Plain, row("a.txt").atRest)
    }

    @Test
    fun `a new cipher reseals files the old one sealed`() = runTest {
        register(EncryptionPolicy.Required())
        sealedFile("a.txt")

        register(EncryptionPolicy.Required(AltCipher.id))
        migrator.migrate()

        assertEquals(AltCipher.id, (row("a.txt").atRest as AtRest.Sealed).cipherId)
        assertArrayEquals(Content, read("a.txt"))
    }

    @Test
    fun `a row that changed since the rewrite began is left alone`() = runTest {
        register(EncryptionPolicy.Off)
        File(root, "a.txt").writeBytes(Content)
        index.indexer.refresh(source(EncryptionPolicy.Off))
        val stale = row("a.txt").copy(size = FileSize(1))

        val replaced = index.writer.recordRewritten(source(EncryptionPolicy.Off), stale) { error("must not run") }

        assertFalse(replaced)
    }

    private var policy: EncryptionPolicy = EncryptionPolicy.Off

    private suspend fun register(policy: EncryptionPolicy) {
        this.policy = policy
        storage.sources.upsert(source(policy))
    }

    private fun source(policy: EncryptionPolicy = this.policy): SourceEntry {
        val base = sourceEntry(id = SourceId, location = SourceLocation.Directory(root.absolutePath), role = SourceEntry.Role.Initiator)
        return base.copy(preferences = base.preferences.copy(encryption = policy))
    }

    /** Placed through the seam, as a peer's upload lands, and indexed. */
    private suspend fun sealedFile(path: String) {
        val staged = node.openStaging().createFile("upload-$path").also { f -> f.openWriter().use { it.write(0, Content) } }
        SourceFileSystems(node, storage, sealedFiles).open(source()).place(staged, path)
        index.indexer.refresh(source())
    }

    private suspend fun read(path: String): ByteArray {
        val fs = SourceFileSystems(node, storage, sealedFiles).open(source())
        return fs.openFile(row(path).locator)!!.read().use { it.readBytes() }
    }

    private suspend fun row(path: String): LocalIndexedFile =
        storage.index.findFile(IndexedFileKey(fileId = SourcePaths.fileId(path), sourceId = SourceId))!!

    private companion object {
        const val SourceId = "source-1"
        val Content = "migrate me, keep my version ".repeat(5000).toByteArray()
    }
}
