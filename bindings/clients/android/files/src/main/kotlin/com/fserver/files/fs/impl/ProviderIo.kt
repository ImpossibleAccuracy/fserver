package com.fserver.files.fs.impl

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.fserver.common.exception.FileSystemException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer

// Byte I/O for files served by a ContentProvider: the same call for every provider, unlike
// creating, renaming and deleting.

@SuppressLint("Recycle")
internal suspend fun readProviderFile(context: Context, uri: Uri): InputStream =
    withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)
            ?: throw FileSystemException.InvalidPath(uri.toString())
    }

/**
 * Positional, because chunks may land out of order — so a provider that hands back a pipe instead
 * of a seekable descriptor cannot serve a transfer here at all.
 */
@SuppressLint("Recycle")
internal suspend fun writeProviderFile(
    context: Context,
    uri: Uri,
    offset: Long,
    bytes: ByteArray,
    length: Int,
): Unit = withContext(Dispatchers.IO) {
    val descriptor = context.contentResolver.openFileDescriptor(uri, "rw")
        ?: throw FileSystemException.InvalidPath(uri.toString())

    descriptor.use { pfd ->
        // Built from the descriptor, so the stream does not own it: only `pfd` closes the fd.
        val channel = FileOutputStream(pfd.fileDescriptor).channel
        val buffer = ByteBuffer.wrap(bytes, 0, length)
        var written = 0L

        while (buffer.hasRemaining()) {
            written += channel.write(buffer, offset + written)
        }
    }
}

internal fun Cursor.longOrZero(column: Int): Long =
    if (isNull(column)) 0L else getLong(column)
