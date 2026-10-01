package com.fserver.files.fs.impl.local

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ReaderTest {

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    @Test
    fun `reads at any offset, and past the end reads nothing`() = runTest {
        val root = temp.newFolder("source")
        File(root, "a.txt").writeText("hello world")
        val file = DirectoryFileSystem(root).openFile(File(root, "a.txt").absolutePath)!!

        file.openReader().use { reader ->
            val buffer = ByteArray(5)

            assertEquals(11L, reader.size())
            assertEquals(5, reader.read(6, buffer))
            assertEquals("world", String(buffer))
            assertEquals(-1, reader.read(11, buffer))
        }
    }
}
