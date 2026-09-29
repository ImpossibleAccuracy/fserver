package com.fserver.files.fs.impl.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.fserver.common.exception.FileSystemException
import com.fserver.common.model.FileSize
import com.fserver.common.task.ProgressTask
import com.fserver.common.utils.SourcePaths
import com.fserver.files.fs.FileSystem
import com.fserver.files.fs.FsFile
import com.fserver.files.fs.impl.longOrZero
import com.fserver.files.fs.impl.mimeTypeOf
import com.fserver.files.fs.impl.partNameOf
import com.fserver.files.fs.impl.placeByCopy
import com.fserver.files.fs.impl.segmentsOf
import com.fserver.files.fs.scan.FoundFile
import com.fserver.files.fs.scan.ScanProgress
import com.fserver.files.fs.scan.scanTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.time.Instant

/**
 * `Download/<directory>/` on the primary volume, through `MediaStore.Downloads`: no permission
 * needed, and only rows this app wrote are visible. Paths are relative to that directory, the way
 * [com.fserver.files.fs.impl.local.DirectoryFileSystem] reports them.
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal class DownloadsFileSystem(
    private val context: Context,
    directory: String,
) : FileSystem {
    private val collection: Uri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    /** `RELATIVE_PATH` of the directory itself, trailing slash included as MediaStore stores it. */
    private val root = "${Environment.DIRECTORY_DOWNLOADS}/$directory/"

    override fun scan(): ProgressTask<ScanProgress, List<FoundFile>> = scanTask(::scanFiles)

    private suspend fun scanFiles(
        onFileFound: (FoundFile) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.RELATIVE_PATH,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.DATE_MODIFIED,
        )

        context.contentResolver
            .query(collection, projection, UnderSelection, arrayOf(likePrefix(root)), null)
            ?.use { cursor ->
                while (cursor.moveToNext() && currentCoroutineContext().isActive) {
                    val name = cursor.getString(3) ?: continue
                    val relativePath = cursor.getString(2) ?: continue

                    // LIKE is case-insensitive for ASCII; a sibling differing only in case is not ours.
                    if (!relativePath.startsWith(root)) continue

                    onFileFound(
                        FoundFile(
                            path = SourcePaths.canonical(
                                volume = null,
                                segments = listOf(relativePath.removePrefix(root), name),
                            ),
                            locator = ContentUris.withAppendedId(collection, cursor.getLong(0)).toString(),
                            size = FileSize(cursor.longOrZero(1)),
                            // Seconds, as MediaStore keeps it.
                            lastModified = Instant.fromEpochSeconds(cursor.longOrZero(4)),
                        )
                    )
                }
            }
    }

    /** Published straight away, as [MediaFileSystem.createFile] explains. */
    override suspend fun createFile(path: String): FsFile = withContext(Dispatchers.IO) {
        val (relativePath, name) = rowOf(path)

        // MediaStore renames a colliding insert instead of refusing it.
        if (findMediaRow(context, collection, relativePath, name) != null) {
            throw FileSystemException.AlreadyExists(path)
        }

        open(insert(path, relativePath, name, pending = false))
    }

    override suspend fun checkPath(path: String) {
        rowOf(path)
    }

    /**
     * Copied into a pending row, which nothing outside this app sees and MediaStore expires if the
     * copy never finishes; published under [path]'s name only once whole.
     */
    override suspend fun place(file: FsFile, path: String): FsFile {
        val (relativePath, name) = rowOf(path)
        val part = withContext(Dispatchers.IO) {
            open(insert(path, relativePath, partNameOf(name), pending = true))
        }

        return placeByCopy(file, part) { created ->
            withContext(Dispatchers.IO) {
                findMediaRow(context, collection, relativePath, name)
                    ?.let { context.contentResolver.delete(it, null, null) }

                publish(created.locator.toUri(), name)
                created
            }
        }
    }

    override suspend fun fileExists(path: String): Boolean = withContext(Dispatchers.IO) {
        val (relativePath, name) = rowOf(path)

        findMediaRow(context, collection, relativePath, name) != null
    }

    override suspend fun openFile(locator: String): FsFile? = withContext(Dispatchers.IO) {
        val uri = locator.toUri()

        if (!isInCollection(uri)) throw FileSystemException.InvalidPath(locator)

        val relativePath = context.contentResolver
            .query(uri, arrayOf(MediaStore.Downloads.RELATIVE_PATH), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0).orEmpty() else null }
            ?: return@withContext null

        if (!relativePath.startsWith(root)) throw FileSystemException.InvalidPath(locator)

        open(uri)
    }

    private fun open(uri: Uri) = MediaFile(context, uri, acceptsName = { true })

    private fun insert(path: String, relativePath: String, name: String, pending: Boolean): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.MIME_TYPE, mimeTypeOf(name))
            if (pending) put(MediaStore.Downloads.IS_PENDING, 1)
        }

        return try {
            context.contentResolver.insert(collection, values)
        } catch (e: IllegalArgumentException) {
            throw FileSystemException.InvalidPath(path)
        } ?: throw FileSystemException.CreationFailed(path)
    }

    /** Clears the pending flag and takes [name]; refused when MediaStore picked another one. */
    private fun publish(uri: Uri, name: String) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.IS_PENDING, 0)
        }

        val published = context.contentResolver.update(uri, values, null, null) > 0 &&
                context.contentResolver
                    .query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)
                    ?.use { it.moveToFirst() && it.getString(0) == name } == true

        if (!published) throw FileSystemException.RenameRejected(uri.toString(), name)
    }

    /** `RELATIVE_PATH` and name for a [path] relative to [root]. */
    private fun rowOf(path: String): Pair<String, String> {
        val segments = segmentsOf(path)
        val relativePath = root + segments.dropLast(1).joinToString("") { "$it/" }

        return relativePath to segments.last()
    }

    /** Same authority, volume and collection as [collection]: a locator from elsewhere is refused. */
    private fun isInCollection(uri: Uri): Boolean =
        uri.scheme == collection.scheme &&
                uri.authority == collection.authority &&
                uri.pathSegments.dropLast(1) == collection.pathSegments &&
                uri.lastPathSegment?.toLongOrNull() != null

    private companion object {
        const val UnderSelection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ? ESCAPE '\\'"

        /** [prefix] as a LIKE pattern matching it and anything below. */
        fun likePrefix(prefix: String): String =
            prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
    }
}
