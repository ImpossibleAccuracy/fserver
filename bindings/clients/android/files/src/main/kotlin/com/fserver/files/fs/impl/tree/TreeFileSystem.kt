package com.fserver.files.fs.impl.tree

import com.fserver.files.fs.impl.placeByCopy
import com.fserver.files.fs.impl.partPathOf
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.fserver.common.exception.FileSystemException
import com.fserver.common.model.FileSize
import com.fserver.common.task.ProgressTask
import com.fserver.common.utils.SourcePaths
import com.fserver.files.fs.FileSystem
import com.fserver.files.fs.FileSystemSource
import com.fserver.files.fs.scan.FoundFile
import com.fserver.files.fs.FsFile
import com.fserver.files.fs.scan.ScanProgress
import com.fserver.files.fs.impl.longOrZero
import com.fserver.files.fs.impl.mimeTypeOf
import com.fserver.files.fs.scan.scanTask
import com.fserver.files.fs.impl.segmentsOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import kotlin.time.Instant

internal class TreeFileSystem(
    private val context: Context,
    private val source: FileSystemSource.Tree,
) : FileSystem {
    override fun scan(): ProgressTask<ScanProgress, List<FoundFile>> = scanTask(::scanFiles)

    private suspend fun scanFiles(
        onFileFound: (FoundFile) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val uri = source.path.toUri()

        val document = DocumentFile.fromTreeUri(context, uri)
            ?: DocumentFile.fromSingleUri(context, uri)
            ?: throw FileSystemException.InvalidPath(source.path)

        if (!document.isDirectory) {
            throw FileSystemException.NotDirectory(source.path)
        }

        recursiveScanFiles(
            resolver = context.contentResolver,
            treeUri = document.uri,
            parentDocumentId = DocumentsContract.getDocumentId(document.uri),
            prefix = emptyList(),
            onFileFound = onFileFound,
        )
    }

    /**
     * Scan directory and its subdirectories recursively.
     *
     * [prefix] is grown from display names on the way down rather than parsed out of a document id
     * afterwards: the id format belongs to the provider, and the tree root is what paths here are
     * relative to.
     */
    private suspend fun recursiveScanFiles(
        resolver: ContentResolver,
        treeUri: Uri,
        parentDocumentId: String,
        prefix: List<String>,
        onFileFound: (FoundFile) -> Unit,
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            parentDocumentId,
        )

        resolver
            .query(
                /* uri = */ childrenUri,
                /* projection = */ Projection,
                /* selection = */ null,
                /* selectionArgs = */ null,
                /* sortOrder = */ null,
            )
            ?.use { cursor ->
                while (cursor.moveToNext()) {
                    currentCoroutineContext().ensureActive()

                    val documentId = cursor.getString(ColumnDocumentId) ?: continue
                    val name = cursor.getString(ColumnDisplayName) ?: continue

                    if (cursor.getString(ColumnMimeType) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        recursiveScanFiles(
                            resolver = resolver,
                            treeUri = treeUri,
                            parentDocumentId = documentId,
                            prefix = prefix + name,
                            onFileFound = onFileFound,
                        )
                        continue
                    }

                    val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

                    onFileFound(
                        FoundFile(
                            path = SourcePaths.canonical(volume = null, segments = prefix + name),
                            locator = uri.toString(),
                            size = FileSize(cursor.longOrZero(ColumnSize)),
                            lastModified = Instant.fromEpochMilliseconds(
                                cursor.longOrZero(ColumnLastModified),
                            ),
                        )
                    )
                }
            }
    }

    /**
     * [path] is relative to the granted tree, so the missing directories along it are created too.
     *
     * The mime type is guessed from the extension because a provider appends one of its own to a
     * name whose extension does not match — and the name is what a rescan derives the path from.
     */
    override suspend fun createFile(path: String): FsFile = withContext(Dispatchers.IO) {
        val segments = segmentsOf(path)

        val root = DocumentFile.fromTreeUri(context, source.path.toUri())
            ?: throw FileSystemException.InvalidPath(source.path)

        var parent = root
        for (name in segments.dropLast(1)) {
            val existing = parent.findFile(name)

            parent = when {
                existing == null -> parent.createDirectory(name)
                    ?: throw FileSystemException.CreationFailed(path)

                existing.isDirectory -> existing
                else -> throw FileSystemException.InvalidPath(path)
            }
        }

        val name = segments.last()
        if (parent.findFile(name) != null) throw FileSystemException.AlreadyExists(path)

        val created = DocumentsContract.createDocument(
            /* content = */ context.contentResolver,
            /* parentDocumentUri = */ parent.uri,
            /* mimeType = */ mimeTypeOf(name),
            /* displayName = */ name,
        ) ?: throw FileSystemException.CreationFailed(path)

        TreeFile(context, created)
    }

    override suspend fun checkPath(path: String) {
        segmentsOf(path)
    }

    /** Copied into a part beside the target, then renamed over it - [TreeFile.rename] keeps the old one aside until then. */
    override suspend fun place(file: FsFile, path: String): FsFile {
        val part = createFile(partPathOf(path)) as TreeFile

        return placeByCopy(file, part) { it.rename(segmentsOf(path).last(), deleteOldOnConflict = true) }
    }

    override suspend fun fileExists(path: String): Boolean = withContext(Dispatchers.IO) {
        var current = DocumentFile.fromTreeUri(context, source.path.toUri())
            ?: throw FileSystemException.InvalidPath(source.path)

        for (name in segmentsOf(path)) {
            current = current.findFile(name) ?: return@withContext false
        }

        current.isFile
    }

    /** Only the document's own existence is checked: the uri came from a scan of this tree. */
    override suspend fun openFile(locator: String): FsFile? = withContext(Dispatchers.IO) {
        val uri = locator.toUri()

        val mimeType = try {
            context.contentResolver
                .query(uri, arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) ?: "" else null }
        } catch (e: FileNotFoundException) {
            null
        } ?: return@withContext null

        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
            throw FileSystemException.InvalidPath(locator)
        }

        TreeFile(context, uri)
    }

    companion object {
        private const val ColumnDocumentId = 0
        private const val ColumnMimeType = 1
        private const val ColumnSize = 2
        private const val ColumnDisplayName = 3
        private const val ColumnLastModified = 4

        private val Projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}
