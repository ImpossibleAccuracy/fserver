package com.fserver.files.fs.impl.local

import com.fserver.common.exception.FileSystemException
import com.fserver.common.utils.SourcePaths
import com.fserver.files.fs.FileSystemSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

/**
 * A source spanning whole volumes.
 *
 * Two properties carry everything else: a path is volume-led on both sides of a sync, and the
 * mounted volumes are the only bound a locator has.
 */
class RootFileSystemTest {

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private lateinit var primary: File
    private lateinit var card: File
    private lateinit var outside: File
    private lateinit var fs: RootFileSystem

    @Before
    fun setUp() {
        primary = temp.newFolder("primary")
        card = temp.newFolder("card")
        outside = temp.newFolder("outside")

        fs = RootFileSystem(
            FileSystemSource.Root(
                volumes = listOf(
                    FileSystemSource.Root.Volume(id = SourcePaths.PrimaryVolume, path = primary.path),
                    FileSystemSource.Root.Volume(id = "1A2B-3C4D", path = card.path),
                ),
            )
        )
    }

    @Test
    fun `a scan leads every path with the volume it was found on`() = runTest {
        File(primary, "DCIM").mkdirs()
        File(primary, "DCIM/a.jpg").writeText("a")
        File(card, "b.txt").writeText("b")

        val found = fs.scan().result().getOrThrow()

        assertEquals(
            setOf("primary/DCIM/a.jpg", "1A2B-3C4D/b.txt"),
            found.map { it.path }.toSet(),
        )
    }

    @Test
    fun `a file is created on the volume its path names, with its directories`() = runTest {
        val file = fs.createFile("1A2B-3C4D/photos/2024/a.jpg")

        assertEquals(File(card, "photos/2024/a.jpg").canonicalPath, File(file.locator).canonicalPath)
        assertTrue(File(card, "photos/2024/a.jpg").exists())
        assertFalse(File(primary, "photos/2024/a.jpg").exists())
    }

    @Test
    fun `a path leading with a volume this source does not span is refused`() = runTest {
        val failure = runCatching { fs.createFile("some-other-card/a.jpg") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
    }

    @Test
    fun `the volume alone is a directory, not a file the peer may create`() = runTest {
        val failure = runCatching { fs.createFile("primary") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
    }

    @Test
    fun `a path that walks off the volume is refused`() = runTest {
        val failure = runCatching { fs.createFile("primary/../outside/evil.txt") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
        assertFalse(File(outside, "evil.txt").exists())
    }

    @Test
    fun `a symlink off the volume does not carry writes out with it`() = runTest {
        Files.createSymbolicLink(File(primary, "link").toPath(), outside.toPath())

        val failure = runCatching { fs.createFile("primary/link/evil.txt") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
        assertFalse(File(outside, "evil.txt").exists())
    }

    @Test
    fun `a file that is already there is not silently overwritten`() = runTest {
        fs.createFile("primary/a.txt")

        val failure = runCatching { fs.createFile("primary/a.txt") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.AlreadyExists)
    }

    @Test
    fun `writes land at the offset they were given and read back whole`() = runTest {
        val file = fs.createFile("primary/a.txt")

        // Out of order on purpose: chunks arrive the way the link delivers them.
        file.write(offset = 6, bytes = "world".toByteArray())
        file.write(offset = 0, bytes = "hello ".toByteArray())

        assertEquals("hello world", file.read().use { String(it.readBytes()) })
    }

    @Test
    fun `only the requested length of a chunk is written`() = runTest {
        val file = fs.createFile("primary/a.txt")

        file.write(offset = 0, bytes = "abcdef".toByteArray(), length = 3)

        assertEquals("abc", File(file.locator).readText())
    }

    @Test
    fun `a locator on another volume of the same source is still reachable`() = runTest {
        val target = File(card, "shared.txt").apply { writeText("shared") }

        assertEquals("shared", fs.openFile(target.absolutePath)!!.read().use { String(it.readBytes()) })
    }

    @Test
    fun `a locator outside every volume cannot be read through`() = runTest {
        val target = File(outside, "secret.txt").apply { writeText("secret") }

        val failure = runCatching { fs.openFile(target.absolutePath) }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
    }

    @Test
    fun `a locator outside every volume cannot be written through`() = runTest {
        val target = File(outside, "secret.txt").apply { writeText("secret") }

        val failure = runCatching {
            fs.openFile(target.absolutePath)!!.write(offset = 0, bytes = "overwritten".toByteArray())
        }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
        assertEquals("secret", target.readText())
    }

    @Test
    fun `a locator outside every volume cannot be deleted through`() = runTest {
        val target = File(outside, "secret.txt").apply { writeText("secret") }

        val failure = runCatching { fs.openFile(target.absolutePath)?.delete() }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
        assertTrue(target.exists())
    }

    @Test
    fun `a file is deleted, and deleting it again is not an error`() = runTest {
        val file = fs.createFile("primary/a.txt")

        assertTrue(file.delete())
        assertFalse(File(file.locator).exists())
        assertTrue(file.delete())
    }

    @Test
    fun `a directory is not a file that can be opened`() = runTest {
        val directory = File(primary, "DCIM").apply { mkdirs() }

        val failure = runCatching { fs.openFile(directory.absolutePath) }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
        assertTrue(directory.exists())
    }
}
