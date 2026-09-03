package com.fserver.files.fs.impl

import android.annotation.SuppressLint
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
import java.io.InputStream
import kotlin.time.Instant

internal class TreeFileSystem(
    private val context: Context,
    private val source: FileSystemSource.Tree,
) : SystemAdapter() {
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

    override suspend fun createFile(path: String): String {
        TODO("Not yet implemented")
    }

    @SuppressLint("Recycle")
    override suspend fun openFile(locator: String): InputStream {
        val uri = locator.toUri()

        return withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)
                ?: throw FileSystemException.InvalidPath(locator)
        }
    }

    override suspend fun writeFile(
        locator: String,
        offset: Long,
        bytes: ByteArray,
        length: Int
    ): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun deleteFile(locator: String): Boolean {
        val uri = locator.toUri()

        return withContext(Dispatchers.IO) {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        }
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
