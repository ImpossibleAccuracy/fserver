package com.fserver.files.fs.impl.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.fserver.common.exception.FileSystemException
import com.fserver.files.fs.FsFile
import com.fserver.files.fs.FsWriter
import com.fserver.files.fs.impl.StreamTarget
import com.fserver.files.fs.impl.openProviderOutput
import com.fserver.files.fs.impl.isMediaName
import com.fserver.files.fs.impl.longOrZero
import com.fserver.files.fs.impl.nameOf
import com.fserver.files.fs.impl.readProviderFile
import com.fserver.files.fs.impl.tree.TreeFile
import com.fserver.files.fs.impl.openProviderWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import kotlin.time.Instant

/** A MediaStore row; the locator is its uri, which a rename keeps. */
@RequiresApi(Build.VERSION_CODES.Q)
internal class MediaFile(
    private val context: Context,
    private val uri: Uri,
) : FsFile, StreamTarget {
    override val locator: String = uri.toString()

    override suspend fun read(): InputStream = readProviderFile(context, uri)

    override suspend fun openWriter(): FsWriter = openProviderWriter(context, uri)

    override suspend fun openOutput(): OutputStream = openProviderOutput(context, uri)

    /** Same dance as [TreeFile.rename]: a row in the way is moved aside, not deleted. */
    override suspend fun rename(
        newName: String,
        deleteOldOnConflict: Boolean,
    ): FsFile = withContext(Dispatchers.IO) {
        val name = nameOf(newName)

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
                Triple(
                    it.getString(0) ?: return@use null,
                    it.getString(1).orEmpty(),
                    it.getString(2) ?: return@use null
                )
            }
            ?: throw FileSystemException.InvalidPath(locator)

        if (original == name) return@withContext this@MediaFile

        val existing =
            findMediaRow(context, MediaStore.Files.getContentUri(volume), relativePath, name)
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
        this@MediaFile
    }

    override suspend fun delete(): Boolean = withContext(Dispatchers.IO) {
        val deleted = try {
            context.contentResolver.delete(uri, null, null) > 0
        } catch (e: SecurityException) {
            // Includes the RecoverableSecurityException for a row another app owns.
            return@withContext false
        }

        // A row that is already gone counts as deleted, so a repeated delete is not a failure.
        deleted || !mediaRowExists(context, uri)
    }

    /** MediaStore owns DATE_MODIFIED, so this only reads back what a scan will see. */
    override suspend fun settleLastModified(time: Instant): Instant =
        withContext(Dispatchers.IO) {
            context.contentResolver
                .query(uri, arrayOf(MediaStore.Files.FileColumns.DATE_MODIFIED), null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) throw FileSystemException.InvalidPath(locator)
                    // Seconds, as the scan reads it.
                    Instant.fromEpochSeconds(cursor.longOrZero(0))
                }
                ?: throw FileSystemException.InvalidPath(locator)
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

    private companion object {
        const val AsideSuffix = ".fserver-replaced"
    }
}

/** The row at [relativePath] + [name] in [collection], or null when there is none. */
internal fun findMediaRow(
    context: Context,
    collection: Uri,
    relativePath: String,
    name: String
): Uri? {
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

internal fun mediaRowExists(context: Context, uri: Uri): Boolean =
    context.contentResolver
        .query(uri, arrayOf(MediaStore.Files.FileColumns._ID), null, null, null)
        ?.use { it.count > 0 }
        ?: false
