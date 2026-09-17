package com.fserver.files.fs.impl

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.fserver.common.exception.FileSystemException
import com.fserver.common.model.FileSize
import com.fserver.common.utils.SourcePaths
import com.fserver.files.fs.FoundFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.time.Instant

/** Every image, video and audio file the MediaStore indexes, newest first. */
@RequiresApi(Build.VERSION_CODES.Q)
internal class MediaFileSystem(
    context: Context,
) : ProviderFileSystem(context) {
    override suspend fun scanFiles(
        onFileFound: (FoundFile) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
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
                            locator = uri.toString(),
                            size = FileSize(
                                if (cursor.isNull(sizeIndex)) 0L else cursor.getLong(sizeIndex),
                            ),
                            // MediaStore counts DATE_MODIFIED in seconds, unlike every other API here.
                            lastModified = Instant.fromEpochSeconds(
                                if (cursor.isNull(modifiedIndex)) 0L else cursor.getLong(
                                    modifiedIndex
                                ),
                            ),
                        )
                    )
                }
            }
    }

    /**
     * [path] is volume-led, the way a scan here reports it, and the segments between the volume and
     * the name become `RELATIVE_PATH`.
     *
     * The row is published straight away rather than staged with `IS_PENDING`: the
     * [com.fserver.files.fs.FileSystem] contract has no call to clear the flag on, and a pending
     * row nothing clears expires instead of arriving.
     */
    override suspend fun createFile(path: String): String = withContext(Dispatchers.IO) {
        val segments = segmentsOf(path)

        // MediaStore keeps every file under a top-level directory it recognises, so
        // "<volume>/<directory>/<name>" is the shortest path it can hold.
        if (segments.size < 3) throw FileSystemException.InvalidPath(path)

        val volume = volumeName(segments.first())

        // Checked before the provider is touched at all: MediaStore answers an unknown volume with
        // a raw IllegalArgumentException, from the query as readily as from the insert.
        if (volume !in MediaStore.getExternalVolumeNames(context)) {
            throw FileSystemException.InvalidPath(path)
        }

        val collection = MediaStore.Files.getContentUri(volume)
        val name = segments.last()
        val relativePath = segments
            .subList(1, segments.size - 1)
            .joinToString(separator = "/", postfix = "/")

        // Checked rather than left to MediaStore, which renames a colliding insert instead of
        // refusing it — and a renamed file no longer matches the path the peer holds.
        if (find(collection, relativePath, name) != null) {
            throw FileSystemException.AlreadyExists(path)
        }

        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, name)
            put(MediaStore.Files.FileColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.Files.FileColumns.MIME_TYPE, mimeTypeOf(name))
        }

        val uri = try {
            context.contentResolver.insert(collection, values)
        } catch (e: IllegalArgumentException) {
            // MediaStore refuses a RELATIVE_PATH whose top-level directory it does not own.
            throw FileSystemException.InvalidPath(path)
        } ?: throw FileSystemException.CreationFailed(path)

        uri.toString()
    }

    override suspend fun deleteFile(locator: String): Boolean = withContext(Dispatchers.IO) {
        val uri = locator.toUri()

        // A row that is already gone counts as deleted, so a repeated delete is not a failure.
        context.contentResolver.delete(uri, null, null) > 0 || !exists(uri)
    }

    /** The row at [relativePath] + [name] in [collection], or null when there is none. */
    private fun find(collection: Uri, relativePath: String, name: String): Uri? {
        val selection = "${MediaStore.Files.FileColumns.RELATIVE_PATH} = ? AND " +
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?"

        context.contentResolver
            .query(
                /* uri = */ collection,
                /* projection = */ arrayOf(MediaStore.Files.FileColumns._ID),
                /* selection = */ selection,
                /* selectionArgs = */ arrayOf(relativePath, name),
                /* sortOrder = */ null,
            )
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return ContentUris.withAppendedId(collection, cursor.getLong(0))
                }
            }

        return null
    }

    private fun exists(uri: Uri): Boolean =
        context.contentResolver
            .query(uri, arrayOf(MediaStore.Files.FileColumns._ID), null, null, null)
            ?.use { it.count > 0 }
            ?: false

    /** Aligned with the volume ids a [com.fserver.files.fs.FileSystemSource.Root] scan reports. */
    private fun volumeId(volumeName: String?): String = when (volumeName) {
        null, MediaStore.VOLUME_EXTERNAL_PRIMARY -> SourcePaths.PrimaryVolume
        else -> volumeName
    }

    /** Inverse of [volumeId]: the MediaStore volume a canonical path's first segment names. */
    private fun volumeName(volumeId: String): String = when (volumeId) {
        SourcePaths.PrimaryVolume -> MediaStore.VOLUME_EXTERNAL_PRIMARY
        else -> volumeId
    }
}
