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
        val (collection, relativePath, name) = rowOf(path)

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

    override suspend fun fileExists(path: String): Boolean = withContext(Dispatchers.IO) {
        val (collection, relativePath, name) = rowOf(path)

        find(collection, relativePath, name) != null
    }

    /** Same dance as [TreeFileSystem.renameFile]: a row in the way is moved aside, not deleted. */
    override suspend fun renameFile(
        locator: String,
        newName: String,
        deleteOldOnConflict: Boolean,
    ): String = withContext(Dispatchers.IO) {
        val name = nameOf(newName)
        val uri = locator.toUri()

        if (!isMediaName(name)) throw FileSystemException.InvalidPath(newName)

        val (volume, relativePath, original) = context.contentResolver
            .query(
                uri,
                arrayOf(
                    MediaStore.Files.FileColumns.VOLUME_NAME,
                    MediaStore.Files.FileColumns.RELATIVE_PATH,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                ),
                null,
                null,
                null,
            )
            ?.use {
                if (!it.moveToFirst()) return@use null
                Triple(it.getString(0) ?: return@use null, it.getString(1).orEmpty(), it.getString(2) ?: return@use null)
            }
            ?: throw FileSystemException.InvalidPath(locator)

        if (original == name) return@withContext locator

        val existing = find(MediaStore.Files.getContentUri(volume), relativePath, name)
        if (existing != null && !deleteOldOnConflict) {
            throw FileSystemException.RenameRejected(locator, newName)
        }

        if (existing != null && !rename(existing, name + AsideSuffix)) {
            throw FileSystemException.RenameRejected(locator, newName)
        }

        if (!rename(uri, name)) {
            existing?.let { rename(it, name) }
            throw FileSystemException.RenameRejected(locator, newName)
        }

        existing?.let { runCatching { context.contentResolver.delete(it, null, null) } }

        // A row keeps its uri through a rename.
        locator
    }

    /** False when MediaStore refused, or quietly picked another name than [name]. */
    private fun rename(uri: Uri, name: String): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, name)
        }

        val updated = try {
            context.contentResolver.update(uri, values, null, null) > 0
        } catch (e: Exception) {
            // Includes the RecoverableSecurityException for a row another app owns.
            false
        }

        return updated && context.contentResolver
            .query(uri, arrayOf(MediaStore.Files.FileColumns.DISPLAY_NAME), null, null, null)
            ?.use { it.moveToFirst() && it.getString(0) == name } == true
    }

    /**
     * The collection, `RELATIVE_PATH` and name a volume-led [path] maps to. MediaStore keeps every
     * file under a top-level directory it recognises, so "<volume>/<directory>/<name>" is the
     * shortest path it can hold.
     */
    private fun rowOf(path: String): Triple<Uri, String, String> {
        val segments = segmentsOf(path)

        if (segments.size < 3) throw FileSystemException.InvalidPath(path)
        if (!isMediaName(segments.last())) throw FileSystemException.InvalidPath(path)

        val volume = volumeName(segments.first())

        // Checked before the provider is touched at all: MediaStore answers an unknown volume with
        // a raw IllegalArgumentException, from the query as readily as from the insert.
        if (volume !in MediaStore.getExternalVolumeNames(context)) {
            throw FileSystemException.InvalidPath(path)
        }

        val relativePath = segments
            .subList(1, segments.size - 1)
            .joinToString(separator = "/", postfix = "/")

        return Triple(MediaStore.Files.getContentUri(volume), relativePath, segments.last())
    }

    override suspend fun deleteFile(locator: String): Boolean = withContext(Dispatchers.IO) {
        val uri = locator.toUri()

        val deleted = try {
            context.contentResolver.delete(uri, null, null) > 0
        } catch (e: SecurityException) {
            // Includes the RecoverableSecurityException for a row another app owns.
            return@withContext false
        }

        // A row that is already gone counts as deleted, so a repeated delete is not a failure.
        deleted || !exists(uri)
    }

    /** MediaStore owns DATE_MODIFIED, so this only reads back what a scan will see. */
    override suspend fun settleLastModified(locator: String, time: Instant): Instant =
        withContext(Dispatchers.IO) {
            context.contentResolver
                .query(
                    locator.toUri(),
                    arrayOf(MediaStore.Files.FileColumns.DATE_MODIFIED),
                    null,
                    null,
                    null,
                )
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) throw FileSystemException.InvalidPath(locator)
                    // Seconds, as the scan reads it.
                    Instant.fromEpochSeconds(if (cursor.isNull(0)) 0L else cursor.getLong(0))
                }
                ?: throw FileSystemException.InvalidPath(locator)
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

    companion object {
        private const val AsideSuffix = ".fserver-replaced"
    }
}
