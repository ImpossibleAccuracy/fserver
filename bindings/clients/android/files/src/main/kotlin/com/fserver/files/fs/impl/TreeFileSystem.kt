package com.fserver.files.fs.impl

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.fserver.common.exception.FileSystemException
import com.fserver.common.model.FileSize
import com.fserver.common.utils.SourcePaths
import com.fserver.files.fs.FoundFile
import com.fserver.files.fs.FileSystemSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.time.Instant

internal class TreeFileSystem(
    context: Context,
    private val source: FileSystemSource.Tree,
) : ProviderFileSystem(context) {
    override suspend fun scanFiles(
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
    override suspend fun createFile(path: String): String = withContext(Dispatchers.IO) {
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

        created.toString()
    }

    override suspend fun deleteFile(locator: String): Boolean {
        val uri = locator.toUri()

        return withContext(Dispatchers.IO) {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        }
    }

    /** A provider owns its documents' mtime, so this only reads back what a scan will see. */
    override suspend fun settleLastModified(locator: String, time: Instant): Instant =
        withContext(Dispatchers.IO) {
            context.contentResolver
                .query(
                    locator.toUri(),
                    arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                    null,
                    null,
                    null,
                )
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) throw FileSystemException.InvalidPath(locator)
                    Instant.fromEpochMilliseconds(cursor.longOrZero(0))
                }
                ?: throw FileSystemException.InvalidPath(locator)
        }

    private fun android.database.Cursor.longOrZero(column: Int): Long =
        if (isNull(column)) 0L else getLong(column)

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
