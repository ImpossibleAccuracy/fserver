package com.fserver.files.fs.impl

import android.content.Context
import com.fserver.common.exception.FileSystemException
import com.fserver.files.fs.FileSystemSource
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
import java.io.File

/**
 * A source reached through the Storage Access Framework, against [TestDocumentsProvider]: document
 * ids, tree uris and the child checks around them still go through `DocumentsContract`.
 */
@RunWith(RobolectricTestRunner::class)
class TreeFileSystemTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private lateinit var root: File
    private lateinit var fs: TreeFileSystem

    @Before
    fun setUp() {
        TestDocumentsProvider.register()

        root = TestDocumentsProvider.rootDirectory(context)
        root.deleteRecursively()
        root.mkdirs()

        fs = TreeFileSystem(
            context = context,
            source = FileSystemSource.Tree(TestDocumentsProvider.treeUri().toString()),
        )
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `a scan reports paths relative to the granted tree`() = runTest {
        File(root, "photos").mkdirs()
        File(root, "photos/a.jpg").writeText("a")
        File(root, "b.txt").writeText("b")

        val found = fs.scan().result().getOrThrow()

        assertEquals(setOf("photos/a.jpg", "b.txt"), found.map { it.path }.toSet())
    }

    @Test
    fun `a file is created under the tree with its directories`() = runTest {
        fs.createFile("photos/2024/a.jpg")

        assertTrue(File(root, "photos/2024/a.jpg").isFile)
    }

    @Test
    fun `a created file keeps the name it was given`() = runTest {
        // The mime type is guessed from the extension precisely so the provider does not append
        // one of its own - a renamed file no longer matches the path the peer holds.
        val locator = fs.createFile("notes/todo.txt")

        val found = fs.scan().result().getOrThrow()

        assertTrue(found.any { it.path == "notes/todo.txt" })
        assertEquals(locator, found.first { it.path == "notes/todo.txt" }.locator)
    }

    @Test
    fun `an existing directory on the way down is reused, not duplicated`() = runTest {
        fs.createFile("photos/a.jpg")
        fs.createFile("photos/b.jpg")

        assertEquals(
            setOf("a.jpg", "b.jpg"),
            File(root, "photos").listFiles().orEmpty().map { it.name }.toSet(),
        )
    }

    @Test
    fun `a file that is already there is not silently overwritten`() = runTest {
        fs.createFile("a.txt")

        val failure = runCatching { fs.createFile("a.txt") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.AlreadyExists)
    }

    @Test
    fun `a directory cannot be created over with a file`() = runTest {
        File(root, "photos").mkdirs()

        val failure = runCatching { fs.createFile("photos") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.AlreadyExists)
    }

    @Test
    fun `a path component that is a file, not a directory, is refused`() = runTest {
        File(root, "photos").writeText("not a directory")

        val failure = runCatching { fs.createFile("photos/a.jpg") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
    }

    @Test
    fun `a path that walks out of the tree is refused`() = runTest {
        val failure = runCatching { fs.createFile("../evil.txt") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
        assertFalse(File(root.parentFile, "evil.txt").exists())
    }

    @Test
    fun `an empty path is refused`() = runTest {
        assertTrue(
            runCatching { fs.createFile("") }.exceptionOrNull() is FileSystemException.InvalidPath,
        )
    }

    @Test
    fun `writes land at the offset they were given and read back whole`() = runTest {
        val locator = fs.createFile("a.txt")

        // Out of order on purpose: chunks arrive the way the link delivers them.
        fs.writeFile(locator, offset = 6, bytes = "world".toByteArray())
        fs.writeFile(locator, offset = 0, bytes = "hello ".toByteArray())

        assertEquals("hello world", fs.openFile(locator).use { String(it.readBytes()) })
    }

    @Test
    fun `only the requested length of a chunk is written`() = runTest {
        val locator = fs.createFile("a.txt")

        fs.writeFile(locator, offset = 0, bytes = "abcdef".toByteArray(), length = 3)

        assertEquals("abc", File(root, "a.txt").readText())
    }

    @Test
    fun `a file is deleted and deleting it again is not an error`() = runTest {
        val locator = fs.createFile("a.txt")

        assertTrue(fs.deleteFile(locator))
        assertFalse(File(root, "a.txt").exists())
        assertTrue(fs.deleteFile(locator))
    }
}
