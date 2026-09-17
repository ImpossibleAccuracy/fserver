package com.fserver.files.fs.impl

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.fserver.common.exception.FileSystemException
import com.fserver.common.utils.SourcePaths
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A source backed by the MediaStore.
 *
 * Instrumented rather than unit-tested: `RELATIVE_PATH`, the rename MediaStore does on a colliding
 * insert and the directories it will accept at all are its own behaviour, not a contract a fake
 * resolver could stand in for.
 *
 * Names are camelCase, not backticked: a backticked name puts spaces into the lambda classes
 * `runTest` generates, and D8 refuses those below dex 040.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
@RequiresApi(Build.VERSION_CODES.Q)
class MediaFileSystemTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var fs: MediaFileSystem

    /** Unique per run: a row this test failed to clean up must not fail the next one. */
    private lateinit var directory: String

    private val created = mutableListOf<String>()

    @Before
    fun setUp() {
        fs = MediaFileSystem(context)
        directory = "Download/FServerFilesTest-${System.nanoTime()}"
    }

    @After
    fun tearDown() {
        created.forEach { locator ->
            runCatching { context.contentResolver.delete(locator.toUri(), null, null) }
        }
    }

    @Test
    fun aFileIsCreatedAtTheRelativePathItsCanonicalPathNames() = runTest {
        val locator = create("a.jpg")

        val relativePath = read(locator, MediaStore.Files.FileColumns.RELATIVE_PATH)
        val name = read(locator, MediaStore.Files.FileColumns.DISPLAY_NAME)

        assertEquals("$directory/", relativePath)
        assertEquals("a.jpg", name)
    }

    @Test
    fun theMimeTypeIsDerivedFromTheNameSoMediastoreKeepsTheName() = runTest {
        val locator = create("a.jpg")

        assertEquals("image/jpeg", read(locator, MediaStore.Files.FileColumns.MIME_TYPE))
        assertEquals("a.jpg", read(locator, MediaStore.Files.FileColumns.DISPLAY_NAME))
    }

    @Test
    fun aFileThatIsAlreadyThereIsNotSilentlyRenamedIntoASecondOne() = runTest {
        create("a.jpg")

        val failure = runCatching { create("a.jpg") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.AlreadyExists)
    }

    @Test
    fun writesLandAtTheOffsetTheyWereGivenAndReadBackWhole() = runTest {
        val locator = create("a.txt")

        // Out of order on purpose: chunks arrive the way the link delivers them.
        assertTrue(fs.writeFile(locator, offset = 6, bytes = "world".toByteArray()))
        assertTrue(fs.writeFile(locator, offset = 0, bytes = "hello ".toByteArray()))

        assertEquals("hello world", fs.openFile(locator).use { String(it.readBytes()) })
    }

    @Test
    fun onlyTheRequestedLengthOfAChunkIsWritten() = runTest {
        val locator = create("a.txt")

        fs.writeFile(locator, offset = 0, bytes = "abcdef".toByteArray(), length = 3)

        assertEquals("abc", fs.openFile(locator).use { String(it.readBytes()) })
    }

    @Test
    fun aFileIsDeletedAndDeletingItAgainIsNotAnError() = runTest {
        val locator = create("a.txt")

        assertTrue(fs.deleteFile(locator))
        assertTrue(fs.deleteFile(locator))
    }

    @Test
    fun aPathWithNoDirectoryBetweenTheVolumeAndTheNameIsRefused() = runTest {
        // MediaStore keeps every file under a top-level directory it recognises.
        val failure = runCatching { fs.createFile("primary/a.jpg") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
    }

    @Test
    fun aPathThatWalksOutIsRefused() = runTest {
        val failure = runCatching { fs.createFile("primary/../../etc/passwd") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
    }

    @Test
    fun aTopLevelDirectoryMediastoreDoesNotOwnIsRefused() = runTest {
        val failure = runCatching { fs.createFile("primary/NotAMediaDir/a.jpg") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
    }

    @Test
    fun aVolumeThatDoesNotExistIsRefused() = runTest {
        val failure = runCatching { fs.createFile("no-such-volume/$directory/a.jpg") }
            .exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
    }

    private suspend fun create(name: String): String =
        fs.createFile("${SourcePaths.PrimaryVolume}/$directory/$name")
            .also { created += it }

    private fun read(locator: String, column: String): String? =
        context.contentResolver
            .query(locator.toUri(), arrayOf(column), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
}
