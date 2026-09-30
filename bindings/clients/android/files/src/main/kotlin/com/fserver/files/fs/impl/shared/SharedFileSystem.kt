package com.fserver.files.fs.impl.shared

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.fserver.common.exception.FileSystemException
import com.fserver.common.model.FileSize
import com.fserver.common.task.ProgressTask
import com.fserver.files.fs.FsFile
import com.fserver.files.fs.FsWriter
import com.fserver.files.fs.ReadableFileSystem
import com.fserver.files.fs.impl.readProviderFile
import com.fserver.files.fs.scan.FoundFile
import com.fserver.files.fs.scan.ScanProgress
import com.fserver.files.fs.scan.scanTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.time.Instant

/**
 * The `content://` uris another app shared - see [com.fserver.files.fs.ReadableSource.Shared].
 * Only those uris open, and nothing here writes: the files belong to whoever shared them.
 */
internal class SharedFileSystem(
    private val context: Context,
    uris: List<String>,
) : ReadableFileSystem {
    private val uris: Set<String> = uris.toSet()

    init {
        // A file:// uri would let a caller point this at app-private files.
        uris.forEach { if (it.toUri().scheme != ContentResolver.SCHEME_CONTENT) throw FileSystemException.InvalidPath(it) }
    }

    /** Fails on the first uri that cannot be read: a set missing a file is not the one shared. */
    override fun scan(): ProgressTask<ScanProgress, List<FoundFile>> = scanTask { onFileFound ->
        withContext(Dispatchers.IO) {
            for (locator in uris) {
                currentCoroutineContext().ensureActive()
                onFileFound(describe(locator))
            }
        }
    }

    override suspend fun openFile(locator: String): FsFile? {
        if (locator !in uris) throw FileSystemException.InvalidPath(locator)
        return SharedFile(context, locator.toUri())
    }

    private fun describe(locator: String): FoundFile {
        val uri = locator.toUri()
        val resolver = context.contentResolver

        val row = try {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null

                fun long(column: String) = cursor.getColumnIndex(column)
                    .takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let(cursor::getLong)

                Row(
                    name = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        .takeIf { it >= 0 }
                        ?.let(cursor::getString),
                    size = long(OpenableColumns.SIZE),
                    // Optional, and only document providers have it at all.
                    modified = long(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                )
            }
        } catch (e: SecurityException) {
            throw FileSystemException.InvalidPath(locator)
        } ?: throw FileSystemException.InvalidPath(locator)

        return FoundFile(
            path = row.name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: FallbackName,
            locator = locator,
            // Optional for a provider; counting the bytes is the one answer left.
            size = FileSize(row.size?.takeIf { it >= 0 } ?: measure(uri)),
            lastModified = Instant.fromEpochMilliseconds(row.modified ?: 0),
        )
    }

    private fun measure(uri: Uri): Long = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(MeasureBufferSize)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                total += read
            }
            total
        } ?: throw FileSystemException.InvalidPath(uri.toString())
    } catch (e: SecurityException) {
        throw FileSystemException.InvalidPath(uri.toString())
    }

    private class Row(val name: String?, val size: Long?, val modified: Long?)

    private companion object {
        const val FallbackName = "file"
        const val MeasureBufferSize = 64 * 1024
    }
}

/** Read-only: nothing here may change a file another app owns. */
private class SharedFile(
    private val context: Context,
    private val uri: Uri,
) : FsFile {
    override val locator: String = uri.toString()

    override suspend fun read(): InputStream = try {
        readProviderFile(context, uri)
    } catch (e: SecurityException) {
        // The grant ended: the sharing task is gone, or the process restarted since.
        throw FileSystemException.InvalidPath(locator)
    }

    override suspend fun openWriter(): FsWriter = throw FileSystemException.InvalidPath(locator)

    override suspend fun rename(newName: String, deleteOldOnConflict: Boolean): FsFile =
        throw FileSystemException.RenameRejected(locator, newName)

    override suspend fun delete(): Boolean = false

    override suspend fun settleLastModified(time: Instant): Instant = time
}
