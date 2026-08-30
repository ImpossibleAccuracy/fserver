package com.fserver.core.files.scan.impl

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.fserver.core.files.model.FileSize
import com.fserver.core.files.model.FileSystemException
import com.fserver.core.files.scan.ScannedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal object RecursiveTreeScanner {
    /** Process a directory tree recursively. */
    suspend fun scanTree(
        context: Context,
        dirPath: String,
        onFileFound: (ScannedFile) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val uri = dirPath.toUri()

        val document = DocumentFile.fromTreeUri(context, uri)
            ?: DocumentFile.fromSingleUri(context, uri)
            ?: throw FileSystemException.InvalidPath(dirPath)

        if (!document.isDirectory) {
            throw FileSystemException.NotDirectory(dirPath)
        }

        recursiveScanFiles(
            resolver = context.contentResolver,
            treeUri = document.uri,
            parentDocumentId = DocumentsContract.getDocumentId(document.uri),
            onFileFound = onFileFound,
        )
    }

    /** Scan directory and its subdirectories recursively. */
    private suspend fun recursiveScanFiles(
        resolver: ContentResolver,
        treeUri: Uri,
        parentDocumentId: String,
        onFileFound: (ScannedFile) -> Unit,
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            parentDocumentId,
        )
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocumentId)

        resolver
            .query(
                /* uri = */ childrenUri,
                /* projection = */
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                ),
                /* selection = */ null,
                /* selectionArgs = */ null,
                /* sortOrder = */ null,
            )
            ?.use { cursor ->
                while (cursor.moveToNext()) {
                    currentCoroutineContext().ensureActive()

                    val documentId = cursor.getString(0) ?: continue

                    if (cursor.getString(1) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        recursiveScanFiles(resolver, treeUri, documentId, onFileFound)
                        continue
                    }

                    onFileFound(
                        ScannedFile(
                            path = DocumentsContract
                                .buildDocumentUriUsingTree(treeUri, documentId)
                                .toString(),
                            directory = parentUri.toString(),
                            size = FileSize(if (cursor.isNull(2)) 0L else cursor.getLong(2)),
                        )
                    )
                }
            }
    }
}
