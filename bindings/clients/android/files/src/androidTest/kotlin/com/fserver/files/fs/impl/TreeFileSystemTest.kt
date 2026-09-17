package com.fserver.files.fs.impl

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import java.io.File

/**
 * A source reached through the Storage Access Framework.
 *
 * Instrumented rather than unit-tested: document ids, tree uris and the child checks around them
 * are the framework's, and a fake resolver would only assert that the fake agrees with itself.
 *
 * Names are camelCase, not backticked: a backticked name puts spaces into the lambda classes
 * `runTest` generates, and D8 refuses those below dex 040.
 */
@RunWith(AndroidJUnit4::class)
class TreeFileSystemTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var root: File
    private lateinit var fs: TreeFileSystem

    @Before
    fun setUp() {
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
    fun aScanReportsPathsRelativeToTheGrantedTree() = runTest {
        File(root, "photos").mkdirs()
        File(root, "photos/a.jpg").writeText("a")
        File(root, "b.txt").writeText("b")

        val found = fs.scan().result().getOrThrow()

        assertEquals(setOf("photos/a.jpg", "b.txt"), found.map { it.path }.toSet())
    }

    @Test
    fun aFileIsCreatedUnderTheTreeWithItsDirectories() = runTest {
        fs.createFile("photos/2024/a.jpg")

        assertTrue(File(root, "photos/2024/a.jpg").isFile)
    }

    @Test
    fun aCreatedFileKeepsTheNameItWasGiven() = runTest {
        // The mime type is guessed from the extension precisely so the provider does not append
        // one of its own - a renamed file no longer matches the path the peer holds.
        val locator = fs.createFile("notes/todo.txt")

        val found = fs.scan().result().getOrThrow()

        assertTrue(found.any { it.path == "notes/todo.txt" })
        assertEquals(locator, found.first { it.path == "notes/todo.txt" }.locator)
    }

    @Test
    fun anExistingDirectoryOnTheWayDownIsReusedNotDuplicated() = runTest {
        fs.createFile("photos/a.jpg")
        fs.createFile("photos/b.jpg")

        assertEquals(
            setOf("a.jpg", "b.jpg"),
            File(root, "photos").listFiles().orEmpty().map { it.name }.toSet(),
        )
    }

    @Test
    fun aFileThatIsAlreadyThereIsNotSilentlyOverwritten() = runTest {
        fs.createFile("a.txt")

        val failure = runCatching { fs.createFile("a.txt") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.AlreadyExists)
    }

    @Test
    fun aDirectoryCannotBeCreatedOverWithAFile() = runTest {
        File(root, "photos").mkdirs()

        val failure = runCatching { fs.createFile("photos") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.AlreadyExists)
    }

    @Test
    fun aPathComponentThatIsAFileNotADirectoryIsRefused() = runTest {
        File(root, "photos").writeText("not a directory")

        val failure = runCatching { fs.createFile("photos/a.jpg") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
    }

    @Test
    fun aPathThatWalksOutOfTheTreeIsRefused() = runTest {
        val failure = runCatching { fs.createFile("../evil.txt") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
        assertFalse(File(root.parentFile, "evil.txt").exists())
    }

    @Test
    fun anEmptyPathIsRefused() = runTest {
        assertTrue(
            runCatching { fs.createFile("") }.exceptionOrNull() is FileSystemException.InvalidPath,
        )
    }

    @Test
    fun writesLandAtTheOffsetTheyWereGivenAndReadBackWhole() = runTest {
        val locator = fs.createFile("a.txt")

        // Out of order on purpose: chunks arrive the way the link delivers them.
        assertTrue(fs.writeFile(locator, offset = 6, bytes = "world".toByteArray()))
        assertTrue(fs.writeFile(locator, offset = 0, bytes = "hello ".toByteArray()))

        assertEquals("hello world", fs.openFile(locator).use { String(it.readBytes()) })
    }

    @Test
    fun onlyTheRequestedLengthOfAChunkIsWritten() = runTest {
        val locator = fs.createFile("a.txt")

        fs.writeFile(locator, offset = 0, bytes = "abcdef".toByteArray(), length = 3)

        assertEquals("abc", File(root, "a.txt").readText())
    }

    @Test
    fun aFileIsDeleted() = runTest {
        val locator = fs.createFile("a.txt")

        assertTrue(fs.deleteFile(locator))
        assertFalse(File(root, "a.txt").exists())
    }
}
