package com.fserver.files.fs.impl

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import com.fserver.common.exception.FileSystemException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer

private const val DefaultMimeType = "application/octet-stream"

/**
 * Backends served by a ContentProvider, where a locator is a uri and every byte moves through the
 * resolver. Reading and writing is the same call for all of them; creating and deleting is not.
 */
internal abstract class ProviderFileSystem(
    protected val context: Context,
) : SystemAdapter() {

    @SuppressLint("Recycle")
    final override suspend fun openFile(locator: String): InputStream =
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(locator.toUri())
                ?: throw FileSystemException.InvalidPath(locator)
        }

    /**
     * Positional, because chunks may land out of order — so a provider that hands back a pipe
     * instead of a seekable descriptor cannot serve a transfer here at all.
     */
    @SuppressLint("Recycle")
    final override suspend fun writeFile(
        locator: String,
        offset: Long,
        bytes: ByteArray,
        length: Int,
    ): Boolean = withContext(Dispatchers.IO) {
        val descriptor = context.contentResolver.openFileDescriptor(locator.toUri(), "rw")
            ?: return@withContext false

        descriptor.use { pfd ->
            // Built from the descriptor, so the stream does not own it: only `pfd` closes the fd.
            val channel = FileOutputStream(pfd.fileDescriptor).channel
            val buffer = ByteBuffer.wrap(bytes, 0, length)
            var written = 0L

            while (buffer.hasRemaining()) {
                written += channel.write(buffer, offset + written)
            }
        }

        true
    }

    /** Mime type guessed from the extension, so a provider keeps the name it was given. */
    protected fun mimeTypeOf(name: String): String =
        MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: DefaultMimeType
}
