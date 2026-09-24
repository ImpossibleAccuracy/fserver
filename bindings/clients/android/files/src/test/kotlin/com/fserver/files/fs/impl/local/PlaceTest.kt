package com.fserver.files.fs.impl.local

import com.fserver.common.exception.FileSystemException
import com.fserver.files.fs.FsFile
import com.fserver.files.fs.impl.BytesFile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Staging to source: a rename where the mount allows it, a copy renamed over the target where not. */
class PlaceTest {

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private lateinit var stagingRoot: File
    private lateinit var sourceRoot: File
    private lateinit var staging: DirectoryFileSystem
    private lateinit var source: DirectoryFileSystem

    @Before
    fun setUp() {
        stagingRoot = temp.newFolder("staging")
        sourceRoot = temp.newFolder("source")
        staging = DirectoryFileSystem.staging(stagingRoot)
        source = DirectoryFileSystem(sourceRoot)
    }

    @Test
    fun `a staged file lands at the path, with its directories`() = runTest {
        val placed = source.place(staged("hello world"), "photos/a.txt")

        assertEquals("hello world", File(sourceRoot, "photos/a.txt").readText())
        assertEquals(File(sourceRoot, "photos/a.txt").canonicalPath, File(placed.locator).canonicalPath)
    }

    @Test
    fun `a file already at the path is replaced`() = runTest {
        File(sourceRoot, "a.txt").writeText("an older and longer content")

        source.place(staged("new"), "a.txt")

        assertEquals("new", File(sourceRoot, "a.txt").readText())
    }

    @Test
    fun `the staged file and the directories it leaves empty are gone after`() = runTest {
        val from = staged("x")

        source.place(from, "a.txt")

        assertFalse(File(from.locator).exists())
        assertTrue(stagingRoot.listFiles().isNullOrEmpty())
    }

    @Test
    fun `a file another backend holds is copied, and no part is left behind`() = runTest {
        File(sourceRoot, "a.txt").writeText("old")
        val foreign = BytesFile("from a provider")

        source.place(foreign, "a.txt")

        assertEquals("from a provider", File(sourceRoot, "a.txt").readText())
        assertEquals(listOf("a.txt"), sourceRoot.list()!!.toList())
        assertTrue(foreign.deleted)
    }

    @Test
    fun `a path that walks out of the source is refused and the staged file stays`() = runTest {
        val from = staged("x")

        val failure = runCatching { source.place(from, "../outside.txt") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
        assertTrue(File(from.locator).exists())
    }

    private suspend fun staged(content: String): FsFile =
        staging.createFile("source-1/file-1/data").also { file ->
            file.openWriter().use { it.write(offset = 0, bytes = content.toByteArray()) }
        }
}
