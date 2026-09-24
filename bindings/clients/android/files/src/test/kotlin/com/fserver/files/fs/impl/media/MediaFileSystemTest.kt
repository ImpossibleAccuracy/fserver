package com.fserver.files.fs.impl.media

import com.fserver.files.fs.impl.BytesFile
import android.content.Context
import android.provider.MediaStore
import androidx.core.net.toUri
import com.fserver.common.exception.FileSystemException
import com.fserver.common.utils.SourcePaths
import com.fserver.files.fs.FsFile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** A source backed by the MediaStore, against [FakeMediaProvider]. */
@RunWith(RobolectricTestRunner::class)
class MediaFileSystemTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private lateinit var provider: FakeMediaProvider
    private lateinit var fs: MediaFileSystem

    @Before
    fun setUp() {
        provider = FakeMediaProvider.register()
        fs = MediaFileSystem(context)
    }

    @Test
    fun `a scan reports media under volume-led paths`() = runTest {
        create("a.jpg")
        provider.addForeign("$Directory/", "notes.txt", "text/plain")

        val found = fs.scan().result().getOrThrow()

        assertEquals(listOf("${SourcePaths.PrimaryVolume}/$Directory/a.jpg"), found.map { it.path })
    }

    @Test
    fun `a file is created at the relative path its canonical path names`() = runTest {
        val file = create("a.jpg")

        assertEquals("$Directory/", read(file.locator, MediaStore.Files.FileColumns.RELATIVE_PATH))
        assertEquals("a.jpg", read(file.locator, MediaStore.Files.FileColumns.DISPLAY_NAME))
    }

    @Test
    fun `the mime type is derived from the name`() = runTest {
        val file = create("a.jpg")

        assertEquals("image/jpeg", read(file.locator, MediaStore.Files.FileColumns.MIME_TYPE))
    }

    @Test
    fun `a file that is already there is not silently renamed into a second one`() = runTest {
        create("a.jpg")

        val failure = runCatching { create("a.jpg") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.AlreadyExists)
    }

    @Test
    fun `a file the scan would not report is refused`() = runTest {
        val failure = runCatching { create("a.txt") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
    }

    @Test
    fun `fileExists sees what was created`() = runTest {
        create("a.jpg")

        assertTrue(fs.fileExists(pathOf("a.jpg")))
        assertFalse(fs.fileExists(pathOf("b.jpg")))
    }

    @Test
    fun `a file opens by its locator and a gone one opens as null`() = runTest {
        val file = create("a.jpg")

        assertEquals(file.locator, fs.openFile(file.locator)?.locator)

        file.delete()

        assertNull(fs.openFile(file.locator))
    }

    @Test
    fun `writes land at the offset they were given and read back whole`() = runTest {
        val file = create("a.jpg")

        // Out of order on purpose: chunks arrive the way the link delivers them.
        file.openWriter().use { it.write(offset = 6, bytes = "world".toByteArray()) }
        file.openWriter().use { it.write(offset = 0, bytes = "hello ".toByteArray()) }

        assertEquals("hello world", file.read().use { String(it.readBytes()) })
    }

    @Test
    fun `only the requested length of a chunk is written`() = runTest {
        val file = create("a.jpg")

        file.openWriter().use { it.write(offset = 0, bytes = "abcdef".toByteArray(), length = 3) }

        assertEquals("abc", file.read().use { String(it.readBytes()) })
    }

    @Test
    fun `a file is renamed in place and keeps its locator`() = runTest {
        val file = create("a.jpg")

        assertEquals(file.locator, file.rename("b.jpg").locator)
        assertEquals("b.jpg", read(file.locator, MediaStore.Files.FileColumns.DISPLAY_NAME))
    }

    @Test
    fun `a rename onto a taken name is refused and leaves both files`() = runTest {
        val a = create("a.jpg")
        val b = create("b.jpg")

        val failure = runCatching { a.rename("b.jpg") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.RenameRejected)
        assertEquals("a.jpg", read(a.locator, MediaStore.Files.FileColumns.DISPLAY_NAME))
        assertEquals("b.jpg", read(b.locator, MediaStore.Files.FileColumns.DISPLAY_NAME))
    }

    @Test
    fun `a rename with deleteOldOnConflict replaces the file in the way`() = runTest {
        val a = create("a.jpg")
        val b = create("b.jpg")

        a.rename("b.jpg", deleteOldOnConflict = true)

        assertEquals("b.jpg", read(a.locator, MediaStore.Files.FileColumns.DISPLAY_NAME))
        assertEquals(null, read(b.locator, MediaStore.Files.FileColumns.DISPLAY_NAME))
    }

    @Test
    fun `a rename to a name the scan would not report is refused`() = runTest {
        val file = create("a.jpg")

        val failure = runCatching { file.rename("a.txt") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
        assertEquals("a.jpg", read(file.locator, MediaStore.Files.FileColumns.DISPLAY_NAME))
    }

    @Test
    fun `a file is deleted and deleting it again is not an error`() = runTest {
        val file = create("a.jpg")

        assertTrue(file.delete())
        assertTrue(file.delete())
    }

    @Test
    fun `a row another app owns is not deleted and does not throw`() = runTest {
        val locator = provider.addForeign("$Directory/", "theirs.jpg", "image/jpeg").toString()

        assertFalse(fs.openFile(locator)!!.delete())
        assertEquals("theirs.jpg", read(locator, MediaStore.Files.FileColumns.DISPLAY_NAME))
    }

    @Test
    fun `a path with no directory between the volume and the name is refused`() = runTest {
        // MediaStore keeps every file under a top-level directory it recognises.
        val failure = runCatching { fs.createFile("primary/a.jpg") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
    }

    @Test
    fun `a path that walks out is refused`() = runTest {
        val failure = runCatching { fs.createFile("primary/../../etc/passwd") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
    }

    @Test
    fun `a top-level directory MediaStore does not own is refused`() = runTest {
        val failure = runCatching { fs.createFile("primary/NotAMediaDir/a.jpg") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
    }

    @Test
    fun `a volume that does not exist is refused`() = runTest {
        val failure = runCatching { fs.createFile("no-such-volume/$Directory/a.jpg") }
            .exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
    }

    @Test
    fun `a placed file takes the name of the row it replaces`() = runTest {
        val old = create("a.jpg")
        val staged = BytesFile("new")

        val placed = fs.place(staged, pathOf("a.jpg"))

        assertEquals("a.jpg", read(placed.locator, MediaStore.Files.FileColumns.DISPLAY_NAME))
        assertEquals("new", placed.read().use { String(it.readBytes()) })
        assertEquals(null, fs.openFile(old.locator))
        assertTrue(staged.deleted)
    }

    private suspend fun create(name: String): FsFile = fs.createFile(pathOf(name))

    private fun pathOf(name: String): String = "${SourcePaths.PrimaryVolume}/$Directory/$name"

    private fun read(locator: String, column: String): String? =
        context.contentResolver
            .query(locator.toUri(), arrayOf(column), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }

    private companion object {
        const val Directory = "Pictures/FServer"
    }
}
