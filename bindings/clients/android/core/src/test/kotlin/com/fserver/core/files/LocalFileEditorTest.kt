package com.fserver.core.files

import android.content.ContextWrapper
import com.fserver.common.exception.FileSystemException
import com.fserver.common.exception.SyncException
import com.fserver.common.utils.SourcePaths
import com.fserver.core.files.access.LocalFileEditor
import com.fserver.core.support.FakeRequirementsChecker
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.LocalIndex
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.TestEpoch
import com.fserver.core.support.sourceEntry
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import com.fserver.files.FilesNode
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

/** User edits land on disk and in the index as new versions of our own. */
class LocalFileEditorTest {

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private val clock = MutableTimeProvider(TestEpoch)
    private val storage = FakeStorage(clock = clock)

    private lateinit var root: File
    private lateinit var editor: LocalFileEditor

    @Before
    fun setUp() {
        val node = FilesNode.create(ContextWrapper(null), stagingDir = temp.newFolder("staging"))
        root = temp.newFolder("source-root")
        editor = LocalFileEditor(
            storage,
            node,
            LocalIndex(storage, node, clock).writer,
            FakeRequirementsChecker(),
            clock
        )
    }

    @Test
    fun `create then write indexes the bytes as a new version`() = runTest {
        register()

        val created = editor.create(SourceId, "docs/note.txt")
        val createdVersion = row(created.fileId)!!.version!!.vector
        val written = created.write { it.write(0, Content.toByteArray()) }

        assertEquals(Content, File(root, "docs/note.txt").readText())
        assertEquals(SourcePaths.fileId("docs/note.txt"), written.fileId)
        assertEquals(Content.length.toLong(), written.size.bytes)
        assertTrue(written.state is LocalIndexedFile.State.Present)
        assertEquals(Content, written.read().use { it.readBytes().decodeToString() })

        assertTrue(row(written.fileId)!!.version!!.vector != createdVersion)
    }

    @Test
    fun `a shorter rewrite truncates the old tail`() = runTest {
        register()

        val file = editor.create(SourceId, "a.txt").write { it.write(0, Content.toByteArray()) }
        val rewritten = file.write {
            it.write(0, "hi".toByteArray())
            it.truncate(2)
        }

        assertEquals("hi", File(root, "a.txt").readText())
        assertEquals(2L, rewritten.size.bytes)
    }

    @Test
    fun `create refuses a taken path`() = runTest {
        register()
        editor.create(SourceId, "a.txt")

        assertThrows<FileSystemException.AlreadyExists> { editor.create(SourceId, "a.txt") }
    }

    @Test
    fun `rename moves the file to a new id and leaves a tombstone`() = runTest {
        register()
        val file = editor.create(SourceId, "dir/a.txt").write { it.write(0, Content.toByteArray()) }

        val renamed = file.rename("b.txt")

        assertEquals("dir/b.txt", renamed.path)
        assertEquals(SourcePaths.fileId("dir/b.txt"), renamed.fileId)
        assertEquals(Content, File(root, "dir/b.txt").readText())
        assertFalse(File(root, "dir/a.txt").exists())
        assertTrue(row(file.fileId)!!.state is LocalIndexedFile.State.Deleted)
        assertNull(editor.find(SourceId, file.fileId))
    }

    @Test
    fun `delete removes the bytes and records a deletion`() = runTest {
        register()
        val file = editor.create(SourceId, "a.txt")

        editor.delete(key(file.fileId))

        assertFalse(File(root, "a.txt").exists())
        assertTrue(row(file.fileId)!!.state is LocalIndexedFile.State.Deleted)
    }

    @Test
    fun `an evicted file is deleted without bytes and cannot be read`() = runTest {
        register()
        val file = editor.create(SourceId, "a.txt")
        storage.index.updateFileState(key(file.fileId), LocalIndexedFile.State.Evicted(TestEpoch))

        val evicted = editor.find(SourceId, file.fileId)!!
        assertThrows<java.io.FileNotFoundException> { evicted.read() }

        editor.delete(key(evicted.fileId))
        assertTrue(row(file.fileId)!!.state is LocalIndexedFile.State.Deleted)
    }

    @Test
    fun `a source the peer drives refuses changes`() = runTest {
        register(syncMode = SyncMode.Host, role = SourceEntry.Role.Follower)

        assertThrows<SyncException.ModeForbiddenException> { editor.create(SourceId, "a.txt") }
        assertFalse(File(root, "a.txt").exists())
    }

    private suspend fun register(
        syncMode: SyncMode = SyncMode.Mirror(SyncMode.Mirror.ConflictResolution.LastWriteWins),
        role: SourceEntry.Role = SourceEntry.Role.Initiator,
    ) = storage.sources.upsert(
        sourceEntry(
            id = SourceId,
            location = SourceLocation.Directory(root.absolutePath),
            syncMode = syncMode,
            role = role,
        )
    )

    private fun key(fileId: String) = IndexedFileKey(fileId = fileId, sourceId = SourceId)

    private suspend fun row(fileId: String) = storage.index.findFile(key(fileId))

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
        const val Content = "hello"
    }
}
