package com.fserver.files.scan.impl

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.fserver.common.model.FileSize
import com.fserver.common.utils.SourcePaths
import com.fserver.files.scan.FoundFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.time.Instant

/** Every image, video and audio file the MediaStore indexes, newest first. */
@RequiresApi(Build.VERSION_CODES.Q)
internal object MediaStoreScanner {
    suspend fun scanMedia(
        context: Context,
        onFileFound: (FoundFile) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.VOLUME_NAME,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
        )
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
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val volumeIndex = cursor.getColumnIndexOrThrow(
                    MediaStore.Files.FileColumns.VOLUME_NAME,
                )
                val relativePathIndex = cursor.getColumnIndexOrThrow(
                    MediaStore.Files.FileColumns.RELATIVE_PATH,
                )
                val nameIndex = cursor.getColumnIndexOrThrow(
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                )
                val modifiedIndex = cursor.getColumnIndexOrThrow(
                    MediaStore.Files.FileColumns.DATE_MODIFIED,
                )

                while (cursor.moveToNext() && currentCoroutineContext().isActive) {
                    val uri = ContentUris.withAppendedId(collection, cursor.getLong(idIndex))
                    val name = cursor.getString(nameIndex) ?: continue

                    onFileFound(
                        FoundFile(
                            // RELATIVE_PATH carries a trailing slash; canonical() drops the empty
                            // segment it leaves behind.
                            path = SourcePaths.canonical(
                                volume = volumeId(cursor.getString(volumeIndex)),
                                segments = listOf(
                                    cursor.getString(relativePathIndex).orEmpty(),
                                    name,
                                ),
                            ),
                            size = FileSize(
                                if (cursor.isNull(sizeIndex)) 0L else cursor.getLong(sizeIndex),
                            ),
                            // MediaStore counts DATE_MODIFIED in seconds, unlike every other API here.
                            lastModified = Instant.fromEpochSeconds(
                                if (cursor.isNull(modifiedIndex)) 0L else cursor.getLong(modifiedIndex),
                            ),
                            provider = UriContentProvider(
                                resolver = context.contentResolver,
                                uri = uri,
                            )
                        )
                    )
                }
            }
    }

    /** Aligned with the volume ids a [com.fserver.files.scan.ScanSource.Root] scan reports. */
    private fun volumeId(volumeName: String?): String = when (volumeName) {
        null, MediaStore.VOLUME_EXTERNAL_PRIMARY -> SourcePaths.PrimaryVolume
        else -> volumeName
    }
}
