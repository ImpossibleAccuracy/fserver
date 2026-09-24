package com.fserver.files.fs.impl.media

import android.content.Context
import com.fserver.common.exception.FileSystemException
import com.fserver.common.utils.SourcePaths
import com.fserver.files.fs.FsFile
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * The media source on the devices that predate scoped storage. The volume root is injected; the
 * walk needs a pre-Android-10 MediaStore and is not covered here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LegacyMediaFileSystemTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private lateinit var volume: File
    private lateinit var outside: File
    private lateinit var fs: LegacyMediaFileSystem

    @Before
    fun setUp() {
        volume = File(context.cacheDir, "legacy-volume").apply { deleteRecursively(); mkdirs() }
        outside = File(context.cacheDir, "legacy-outside").apply { deleteRecursively(); mkdirs() }

        fs = LegacyMediaFileSystem(context, externalStorage = volume)
    }

    @After
    fun tearDown() {
        volume.deleteRecursively()
        outside.deleteRecursively()
    }

    @Test
    fun `a file is created under the volume its path leads with`() = runTest {
        val file = fs.createFile("${SourcePaths.PrimaryVolume}/DCIM/2024/a.jpg")

        assertEquals(File(volume, "DCIM/2024/a.jpg").canonicalPath, File(file.locator).canonicalPath)
        assertTrue(File(volume, "DCIM/2024/a.jpg").isFile)
    }

    @Test
    fun `a path leading with another volume is refused`() = runTest {
        // Only the primary volume is indexed here, so no other id can be honoured.
        val failure = runCatching { fs.createFile("1A2B-3C4D/DCIM/a.jpg") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
    }

    @Test
    fun `the volume alone is a directory, not a file the peer may create`() = runTest {
        val failure = runCatching { fs.createFile(SourcePaths.PrimaryVolume) }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
    }

    @Test
    fun `a path that walks off the volume is refused`() = runTest {
        val failure = runCatching {
            fs.createFile("${SourcePaths.PrimaryVolume}/../legacy-outside/evil.txt")
        }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
        assertFalse(File(outside, "evil.txt").exists())
    }

    @Test
    fun `a file the scan would not report is refused`() = runTest {
        val failure = runCatching { create("a.txt") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
        assertFalse(File(volume, "DCIM/a.txt").exists())
    }

    @Test
    fun `a rename to a name the scan would not report is refused`() = runTest {
        val file = create("a.jpg")

        val failure = runCatching { file.rename("a.txt") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
        assertTrue(File(file.locator).isFile)
        assertFalse(File(volume, "DCIM/a.txt").exists())
    }

    @Test
    fun `a file that is already there is not silently overwritten`() = runTest {
        create("a.jpg")

        val failure = runCatching { create("a.jpg") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.AlreadyExists)
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

        assertEquals("abc", File(file.locator).readText())
    }

    @Test
    fun `a locator outside the volume cannot be read through`() = runTest {
        val target = File(outside, "secret.txt").apply { writeText("secret") }

        val failure = runCatching { fs.openFile(target.absolutePath) }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
    }

    @Test
    fun `a locator outside the volume cannot be written through`() = runTest {
        val target = File(outside, "secret.txt").apply { writeText("secret") }

        val failure = runCatching {
            fs.openFile(target.absolutePath)!!.openWriter().use { it.write(offset = 0, bytes = "overwritten".toByteArray()) }
        }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
        assertEquals("secret", target.readText())
    }

    @Test
    fun `a locator outside the volume cannot be deleted through`() = runTest {
        val target = File(outside, "secret.txt").apply { writeText("secret") }

        val failure = runCatching { fs.openFile(target.absolutePath)?.delete() }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
        assertTrue(target.exists())
    }

    @Test
    fun `a file is deleted and deleting it again is not an error`() = runTest {
        val file = create("a.jpg")

        assertTrue(file.delete())
        assertFalse(File(file.locator).exists())
        assertTrue(file.delete())
    }

    private suspend fun create(name: String): FsFile =
        fs.createFile("${SourcePaths.PrimaryVolume}/DCIM/$name")
}
