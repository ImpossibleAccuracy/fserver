package com.fserver.files.fs.impl.local

import com.fserver.common.exception.FileSystemException
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
import java.nio.file.Files

/**
 * The backend a hosted source actually writes through.
 *
 * Every byte and every path here arrived from a peer, so confinement to the source directory is
 * the property under test - not a detail of it.
 */
class DirectoryFileSystemTest {

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private lateinit var root: File
    private lateinit var outside: File
    private lateinit var fs: DirectoryFileSystem

    @Before
    fun setUp() {
        root = temp.newFolder("source-root")
        outside = temp.newFolder("outside")
        fs = DirectoryFileSystem(root)
    }

    @Test
    fun `a file is created under the source, with its directories`() = runTest {
        val file = fs.createFile("photos/2024/a.jpg")

        assertEquals(File(root, "photos/2024/a.jpg").canonicalPath, File(file.locator).canonicalPath)
        assertTrue(File(root, "photos/2024/a.jpg").exists())
    }

    @Test
    fun `every file that appears or goes is reported`() = runTest {
        val changed = mutableListOf<String>()
        val watched = DirectoryFileSystem(root) { changed += it.relativeTo(root).path }

        val file = watched.createFile("a.txt")
        watched.place(watched.createFile("b.txt"), "c.txt")
        file.delete()

        assertEquals(listOf("a.txt", "b.txt", "b.txt", "c.txt", "a.txt"), changed)
    }

    @Test
    fun `a path that walks out of the source is refused`() = runTest {
        val failure = runCatching { fs.createFile("../outside/evil.txt") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
        assertFalse(File(outside, "evil.txt").exists())
    }

    @Test
    fun `a path that walks out and back in is still refused`() = runTest {
        // Resolves back inside, but only after leaving: a check on the raw string would pass it.
        val failure = runCatching { fs.createFile("../source-root/../outside/evil.txt") }
            .exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
        assertFalse(File(outside, "evil.txt").exists())
    }

    @Test
    fun `an absolute path is nested under the source rather than obeyed`() = runTest {
        val target = File(outside, "absolute.txt")

        val file = fs.createFile(target.absolutePath)

        // `File(root, "/x/y")` resolves under root, so an absolute path from a peer lands inside
        // the source instead of at the address it named. Nothing escapes - assert both halves.
        assertTrue(File(file.locator).canonicalPath.startsWith(root.canonicalPath + File.separator))
        assertFalse(target.exists())
    }

    @Test
    fun `the source root itself is not a file the peer may create`() = runTest {
        val failure = runCatching { fs.createFile("") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
    }

    @Test
    fun `a symlink out of the source does not carry writes out with it`() = runTest {
        Files.createSymbolicLink(File(root, "link").toPath(), outside.toPath())

        val failure = runCatching { fs.createFile("link/evil.txt") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
        assertFalse(File(outside, "evil.txt").exists())
    }

    @Test
    fun `a file that is already there is not silently overwritten`() = runTest {
        fs.createFile("a.txt")

        val failure = runCatching { fs.createFile("a.txt") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.AlreadyExists)
    }

    @Test
    fun `writes land at the offset they were given and read back whole`() = runTest {
        val file = fs.createFile("a.txt")

        file.openWriter().use { it.write(offset = 0, bytes = "hello ".toByteArray()) }
        file.openWriter().use { it.write(offset = 6, bytes = "world".toByteArray()) }

        assertEquals("hello world", file.read().use { String(it.readBytes()) })
    }

    @Test
    fun `a locator outside the source cannot be written through`() = runTest {
        val target = File(outside, "secret.txt").apply { writeText("secret") }

        val failure = runCatching {
            fs.openFile(target.absolutePath)!!.openWriter().use { it.write(offset = 0, bytes = "overwritten".toByteArray()) }
        }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
        assertEquals("secret", target.readText())
    }

    @Test
    fun `a locator outside the source cannot be read through`() = runTest {
        val target = File(outside, "secret.txt").apply { writeText("secret") }

        val failure = runCatching { fs.openFile(target.absolutePath) }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
    }

    @Test
    fun `a locator outside the source cannot be deleted through`() = runTest {
        val target = File(outside, "secret.txt").apply { writeText("secret") }

        val failure = runCatching { fs.openFile(target.absolutePath)?.delete() }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
        assertTrue(target.exists())
    }

    @Test
    fun `a locator with nothing behind it opens as null`() = runTest {
        assertNull(fs.openFile(File(root, "never-existed.txt").absolutePath))
    }

    @Test
    fun `a scan reports paths relative to the source, in canonical form`() = runTest {
        File(root, "photos").mkdirs()
        File(root, "photos/a.jpg").writeText("a")
        File(root, "b.txt").writeText("b")

        val found = fs.scan().result().getOrThrow()

        assertEquals(setOf("photos/a.jpg", "b.txt"), found.map { it.path }.toSet())
    }

    @Test
    fun `a source directory that does not exist yet scans empty rather than failing`() =
        runTest {
            val unwritten = DirectoryFileSystem(File(root, "not-created-yet"))

            assertTrue(unwritten.scan().result().getOrThrow().isEmpty())
        }
}
