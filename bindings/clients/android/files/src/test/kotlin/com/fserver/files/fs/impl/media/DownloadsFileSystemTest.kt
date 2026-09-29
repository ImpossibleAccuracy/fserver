package com.fserver.files.fs.impl.media

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import androidx.core.net.toUri
import com.fserver.common.exception.FileSystemException
import com.fserver.files.fs.FsFile
import com.fserver.files.fs.FsWriter
import com.fserver.files.fs.impl.BytesFile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.IOException
import java.io.InputStream
import kotlin.time.Instant

/** `Download/<directory>` through MediaStore.Downloads, against [FakeMediaProvider]. */
@RunWith(RobolectricTestRunner::class)
class DownloadsFileSystemTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private lateinit var provider: FakeMediaProvider
    private lateinit var fs: DownloadsFileSystem

    @Before
    fun setUp() {
        provider = FakeMediaProvider.register()
        fs = DownloadsFileSystem(context, Directory)
    }

    @Test
    fun `a scan reports every type under the directory, relative to it`() = runTest {
        fs.createFile("notes.txt")
        fs.createFile("album/a.jpg")
        insert("Download/", "elsewhere.pdf")
        insert("Download/${Directory}Other/", "sibling.pdf")
        insert("Download/${Directory.lowercase()}/", "case.pdf")

        val found = fs.scan().result().getOrThrow()

        assertEquals(setOf("notes.txt", "album/a.jpg"), found.map { it.path }.toSet())
    }

    @Test
    fun `LIKE wildcards in the directory name match only themselves`() = runTest {
        val underscored = DownloadsFileSystem(context, "F_Server")
        underscored.createFile("mine.txt")
        insert("Download/FxServer/", "theirs.txt")

        assertEquals(listOf("mine.txt"), underscored.scan().result().getOrThrow().map { it.path })
    }

    @Test
    fun `a file is created under the directory, whatever its type`() = runTest {
        val file = fs.createFile("docs/report.pdf")

        assertEquals("Download/$Directory/docs/", read(file.locator, MediaStore.Downloads.RELATIVE_PATH))
        assertEquals("report.pdf", read(file.locator, MediaStore.Downloads.DISPLAY_NAME))
        assertTrue(fs.fileExists("docs/report.pdf"))
    }

    @Test
    fun `a file that is already there is not silently renamed into a second one`() = runTest {
        fs.createFile("a.txt")

        val failure = runCatching { fs.createFile("a.txt") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.AlreadyExists)
    }

    @Test
    fun `a path that walks out is refused`() = runTest {
        val failure = runCatching { fs.createFile("../Pictures/a.jpg") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
    }

    @Test
    fun `a locator outside the directory does not open`() = runTest {
        val sibling = insert("Download/", "elsewhere.pdf").toString()
        val picture = provider.addForeign("Pictures/", "a.jpg", "image/jpeg").toString()

        for (locator in listOf(sibling, picture, "content://evil/downloads/1")) {
            val failure = runCatching { fs.openFile(locator) }.exceptionOrNull()

            assertTrue(locator, failure is FileSystemException.InvalidPath)
        }
    }

    @Test
    fun `a file opens by its locator and a gone one opens as null`() = runTest {
        val file = fs.createFile("a.txt")

        assertEquals(file.locator, fs.openFile(file.locator)?.locator)

        file.delete()

        assertNull(fs.openFile(file.locator))
    }

    @Test
    fun `a file may be renamed to any type`() = runTest {
        val file = fs.createFile("a.jpg")

        file.rename("a.txt")

        assertEquals("a.txt", read(file.locator, MediaStore.Downloads.DISPLAY_NAME))
    }

    @Test
    fun `a placed file is published under its name, replacing the one there`() = runTest {
        val old = fs.createFile("a.txt")
        val staged = BytesFile("new")

        val placed = fs.place(staged, "a.txt")

        assertEquals("a.txt", read(placed.locator, MediaStore.Downloads.DISPLAY_NAME))
        assertEquals("0", read(placed.locator, MediaStore.Downloads.IS_PENDING))
        assertEquals("new", placed.read().use { String(it.readBytes()) })
        assertNull(fs.openFile(old.locator))
        assertTrue(staged.deleted)
        assertEquals(listOf("a.txt"), fs.scan().result().getOrThrow().map { it.path })
    }

    @Test
    fun `a copy that breaks leaves the old file and no part behind`() = runTest {
        val old = fs.createFile("a.txt")
        old.openWriter().use { it.write(0, "old".toByteArray()) }

        val failure = runCatching { fs.place(BrokenFile, "a.txt") }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertEquals("old", old.read().use { String(it.readBytes()) })
        assertEquals(1, provider.rowCount)
    }

    private fun insert(relativePath: String, name: String) = context.contentResolver.insert(
        MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
        ContentValues().apply {
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
        },
    )!!

    private fun read(locator: String, column: String): String? =
        context.contentResolver
            .query(locator.toUri(), arrayOf(column), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }

    /** Fails mid-copy, the way a staging file on a full disk does. */
    private object BrokenFile : FsFile {
        override val locator = "test://broken"
        override suspend fun read(): InputStream = object : InputStream() {
            override fun read(): Int = throw IOException("disk gone")
        }

        override suspend fun openWriter(): FsWriter = throw UnsupportedOperationException()
        override suspend fun rename(newName: String, deleteOldOnConflict: Boolean): FsFile = this
        override suspend fun delete(): Boolean = true
        override suspend fun settleLastModified(time: Instant): Instant = time
    }

    private companion object {
        const val Directory = "FServer"
    }
}
