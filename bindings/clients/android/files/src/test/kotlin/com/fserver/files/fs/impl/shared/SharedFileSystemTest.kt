package com.fserver.files.fs.impl.shared

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.fserver.common.exception.FileSystemException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/** Files another app shares by uri: scanned, read, and never changed. */
@RunWith(RobolectricTestRunner::class)
class SharedFileSystemTest {

    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        File(context.cacheDir, "shared.bin").writeBytes(Content)
        Robolectric.buildContentProvider(SharingProvider::class.java)
            .create(ProviderInfo().apply { authority = Authority })
    }

    @Test
    fun `a scan reports each shared file as its provider describes it`() = runTest {
        val found = fs(Sized, Unsized).scan().result().getOrThrow()

        assertEquals(listOf("photo.jpg", "photo.jpg"), found.map { it.path })
        assertEquals(listOf(Sized, Unsized), found.map { it.locator })
        // The second provider row has no size: counted instead.
        assertEquals(listOf(Content.size.toLong(), Content.size.toLong()), found.map { it.size.bytes })
    }

    @Test
    fun `only content uris are taken`() {
        assertThrows(FileSystemException.InvalidPath::class.java) { fs("file:///data/data/app/secret.db") }
    }

    @Test
    fun `only the shared uris open`() = runTest {
        val failure = runCatching { fs(Sized).openFile(Unsized) }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
    }

    @Test
    fun `a shared file reads but nothing changes`() = runTest {
        val fs = fs(Sized)
        val file = fs.openFile(Sized)!!

        assertEquals(Content.toList(), file.read().use { it.readBytes() }.toList())
        assertFalse(file.delete())
        assertTrue(runCatching { file.rename("x") }.exceptionOrNull() is FileSystemException.RenameRejected)
    }

    private fun fs(vararg uris: String) = SharedFileSystem(context, uris.toList())

    class SharingProvider : ContentProvider() {
        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
            addRow(arrayOf<Any?>("photo.jpg", if (uri.lastPathSegment == "sized") Content.size.toLong() else null))
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = ParcelFileDescriptor.open(
            File(requireNotNull(context).cacheDir, "shared.bin"),
            ParcelFileDescriptor.MODE_READ_ONLY,
        )

        override fun getType(uri: Uri) = "image/jpeg"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }

    private companion object {
        const val Authority = "com.fserver.test.sharing"
        const val Sized = "content://$Authority/sized"
        const val Unsized = "content://$Authority/unsized"
        val Content = ByteArray(1000) { it.toByte() }
    }
}
