package com.fserver.files.fs.impl

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fserver.common.exception.FileSystemException
import com.fserver.common.utils.SourcePaths
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
 * The media source on the devices that predate scoped storage.
 *
 * The volume root is injected, so everything but the walk runs on any api level — the walk is the
 * one part that needs a pre-Android-10 MediaStore and is left to a device that has one.
 *
 * Names are camelCase, not backticked: a backticked name puts spaces into the lambda classes
 * `runTest` generates, and D8 refuses those below dex 040.
 */
@RunWith(AndroidJUnit4::class)
class LegacyMediaFileSystemTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

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
    fun aFileIsCreatedUnderTheVolumeItsPathLeadsWith() = runTest {
        val locator = fs.createFile("${SourcePaths.PrimaryVolume}/DCIM/2024/a.jpg")

        assertEquals(File(volume, "DCIM/2024/a.jpg").canonicalPath, File(locator).canonicalPath)
        assertTrue(File(volume, "DCIM/2024/a.jpg").isFile)
    }

    @Test
    fun aPathLeadingWithAnotherVolumeIsRefused() = runTest {
        // Only the primary volume is indexed here, so no other id can be honoured.
        val failure = runCatching { fs.createFile("1A2B-3C4D/DCIM/a.jpg") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
    }

    @Test
    fun theVolumeAloneIsADirectoryNotAFileThePeerMayCreate() = runTest {
        val failure = runCatching { fs.createFile(SourcePaths.PrimaryVolume) }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
    }

    @Test
    fun aPathThatWalksOffTheVolumeIsRefused() = runTest {
        val failure = runCatching {
            fs.createFile("${SourcePaths.PrimaryVolume}/../legacy-outside/evil.txt")
        }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
        assertFalse(File(outside, "evil.txt").exists())
    }

    @Test
    fun aFileThatIsAlreadyThereIsNotSilentlyOverwritten() = runTest {
        create("a.jpg")

        val failure = runCatching { create("a.jpg") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.AlreadyExists)
    }

    @Test
    fun writesLandAtTheOffsetTheyWereGivenAndReadBackWhole() = runTest {
        val locator = create("a.jpg")

        // Out of order on purpose: chunks arrive the way the link delivers them.
        assertTrue(fs.writeFile(locator, offset = 6, bytes = "world".toByteArray()))
        assertTrue(fs.writeFile(locator, offset = 0, bytes = "hello ".toByteArray()))

        assertEquals("hello world", fs.openFile(locator).use { String(it.readBytes()) })
    }

    @Test
    fun onlyTheRequestedLengthOfAChunkIsWritten() = runTest {
        val locator = create("a.jpg")

        fs.writeFile(locator, offset = 0, bytes = "abcdef".toByteArray(), length = 3)

        assertEquals("abc", File(locator).readText())
    }

    @Test
    fun aLocatorOutsideTheVolumeCannotBeReadThrough() = runTest {
        val target = File(outside, "secret.txt").apply { writeText("secret") }

        val failure = runCatching { fs.openFile(target.absolutePath) }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
    }

    @Test
    fun aLocatorOutsideTheVolumeCannotBeWrittenThrough() = runTest {
        val target = File(outside, "secret.txt").apply { writeText("secret") }

        val failure = runCatching {
            fs.writeFile(target.absolutePath, offset = 0, bytes = "overwritten".toByteArray())
        }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
        assertEquals("secret", target.readText())
    }

    @Test
    fun aLocatorOutsideTheVolumeCannotBeDeletedThrough() = runTest {
        val target = File(outside, "secret.txt").apply { writeText("secret") }

        val failure = runCatching { fs.deleteFile(target.absolutePath) }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
        assertTrue(target.exists())
    }

    @Test
    fun aFileIsDeletedAndDeletingItAgainIsNotAnError() = runTest {
        val locator = create("a.jpg")

        assertTrue(fs.deleteFile(locator))
        assertFalse(File(locator).exists())
        assertTrue(fs.deleteFile(locator))
    }

    private suspend fun create(name: String): String =
        fs.createFile("${SourcePaths.PrimaryVolume}/DCIM/$name")
}
