package com.fserver.files.fs.impl

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.provider.MediaStore
import com.fserver.common.exception.FileSystemException
import com.fserver.common.model.FileSize
import com.fserver.common.utils.SourcePaths
import com.fserver.files.fs.FoundFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Instant

/**
 * Every image, video and audio file the MediaStore indexes, on the devices that predate scoped
 * storage.
 *
 * MediaStore is the index here and nothing more: before Android 10 a row carries the file's real
 * path, so the walk asks the provider which files are media and everything else goes through
 * [File]. Size and timestamp are read off the disk rather than out of the row, because a row this
 * class wrote through goes stale until the media scanner catches up.
 *
 * Paths come out the shape [MediaFileSystem] reports them in, so a peer cannot tell which of the
 * two served it.
 */
internal class LegacyMediaFileSystem(
    private val context: Context,
    externalStorage: File = primaryVolume(),
) : LocalFileSystem() {

    private val root: File = externalStorage.canonicalFile

    @Suppress("DEPRECATION")
    override suspend fun scanFiles(
        onFileFound: (FoundFile) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        val collection = MediaStore.Files.getContentUri(ExternalVolume)
        val projection = arrayOf(MediaStore.Files.FileColumns.DATA)
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?, ?)"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO.toString(),
        )
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        context.contentResolver
            .query(collection, projection, selection, selectionArgs, sortOrder)
            ?.use { cursor ->
                val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)

                while (cursor.moveToNext() && currentCoroutineContext().isActive) {
                    val file = File(cursor.getString(dataIndex) ?: continue).canonicalFile

                    // The index outlives the files on these devices, and a row for a card this
                    // volume does not cover has no volume id a peer could agree with us on.
                    if (!file.isFile || !file.isUnder(root)) continue

                    onFileFound(
                        FoundFile(
                            path = SourcePaths.canonical(
                                volume = SourcePaths.PrimaryVolume,
                                path = file.relativeTo(root).invariantSeparatorsPath,
                            ),
                            locator = file.absolutePath,
                            size = FileSize(file.length()),
                            lastModified = Instant.fromEpochMilliseconds(file.lastModified()),
                        )
                    )
                }
            }
    }

    override fun resolve(path: String): File {
        val segments = segmentsOf(path)

        // One volume is all MediaStore indexes here, so it is the only one a path may name.
        if (segments.first() != SourcePaths.PrimaryVolume) throw FileSystemException.InvalidPath(path)

        // The volume itself is a directory, not a file the peer may create.
        if (segments.size < 2) throw FileSystemException.InvalidPath(path)

        return owned(File(root, segments.drop(1).joinToString("/")).canonicalFile, path)
    }

    override fun confine(locator: String): File = owned(File(locator).canonicalFile, locator)

    /**
     * [file], if this source holds it: under [root] and media. Anything else the scan never
     * reports, so creating or renaming into it would lose the file to the peer.
     */
    private fun owned(file: File, path: String): File = file.also {
        if (!it.isUnder(root) || !isMediaName(it.name)) throw FileSystemException.InvalidPath(path)
    }

    /**
     * A file the index has never heard of is a file the next scan will not report — so the peer
     * would keep sending it, forever.
     */
    override fun onFileChanged(file: File) {
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
    }

    companion object {
        /** `MediaStore.VOLUME_EXTERNAL` in all but name, which only arrived with Android 10. */
        private const val ExternalVolume = "external"

        @Suppress("DEPRECATION")
        fun primaryVolume(): File = Environment.getExternalStorageDirectory()
    }
}
