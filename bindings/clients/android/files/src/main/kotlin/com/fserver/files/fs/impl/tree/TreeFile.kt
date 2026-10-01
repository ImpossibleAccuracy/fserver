package com.fserver.files.fs.impl.tree

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.os.ParcelFileDescriptor
import com.fserver.common.exception.FileSystemException
import com.fserver.files.fs.FsFile
import com.fserver.files.fs.FsReader
import com.fserver.files.fs.FsWriter
import com.fserver.files.fs.impl.StreamTarget
import com.fserver.files.fs.impl.openProviderOutput
import com.fserver.files.fs.impl.longOrZero
import com.fserver.files.fs.impl.nameOf
import com.fserver.files.fs.impl.readProviderFile
import com.fserver.files.fs.impl.openProviderDescriptor
import com.fserver.files.fs.impl.openProviderReader
import com.fserver.files.fs.impl.openProviderWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import kotlin.time.Instant

/** A document in a tree granted through the Storage Access Framework; the locator is its uri. */
internal class TreeFile(
    private val context: Context,
    private val uri: Uri,
) : FsFile, StreamTarget {
    override val locator: String = uri.toString()

    override suspend fun read(): InputStream = readProviderFile(context, uri)

    override suspend fun openReader(): FsReader = openProviderReader(context, uri)

    override suspend fun openDescriptor(): ParcelFileDescriptor = openProviderDescriptor(context, uri)

    override suspend fun openWriter(): FsWriter = openProviderWriter(context, uri)

    override suspend fun openOutput(): OutputStream = openProviderOutput(context, uri)

    /**
     * A file in the way is moved aside rather than deleted, so a failed rename can put it back.
     * Finding it needs the parent, which only [DocumentsContract.findDocumentPath] gives (API 26+,
     * and optional for a provider) - without it a name already taken is a refusal.
     */
    override suspend fun rename(
        newName: String,
        deleteOldOnConflict: Boolean,
    ): FsFile = withContext(Dispatchers.IO) {
        val name = nameOf(newName)
        val original = displayName(uri) ?: throw FileSystemException.InvalidPath(locator)

        if (original == name) return@withContext this@TreeFile

        val existing = parentOf(uri)?.let { childNamed(it, name) }
        if (existing != null && !deleteOldOnConflict) {
            throw FileSystemException.RenameRejected(locator, newName)
        }

        val aside = existing?.let {
            renameDocument(it, name + AsideSuffix)
                ?: throw FileSystemException.RenameRejected(locator, newName)
        }

        // A provider may pick a free name instead of the one asked for, and that is a refusal too.
        val renamed = renameDocument(uri, name)
        if (renamed == null || displayName(renamed) != name) {
            renamed?.let { renameDocument(it, original) }
            aside?.let { renameDocument(it, name) }
            throw FileSystemException.RenameRejected(locator, newName)
        }

        aside?.let { runCatching { DocumentsContract.deleteDocument(context.contentResolver, it) } }

        TreeFile(context, renamed)
    }

    /**
     * API 29+ throws where older releases returned false. Only "not found" counts as deleted: an
     * unreadable document is not known to be gone.
     */
    override suspend fun delete(): Boolean = withContext(Dispatchers.IO) {
        try {
            DocumentsContract.deleteDocument(context.contentResolver, uri) || !exists()
        } catch (e: FileNotFoundException) {
            true
        } catch (e: Exception) {
            // Unsupported by the provider, or the grant is gone.
            false
        }
    }

    /** A provider owns its documents' mtime, so this only reads back what a scan will see. */
    override suspend fun settleLastModified(time: Instant): Instant =
        withContext(Dispatchers.IO) {
            context.contentResolver
                .query(
                    uri,
                    arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                    null,
                    null,
                    null
                )
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) throw FileSystemException.InvalidPath(locator)
                    Instant.fromEpochMilliseconds(cursor.longOrZero(0))
                }
                ?: throw FileSystemException.InvalidPath(locator)
        }

    private fun exists(): Boolean =
        try {
            displayName(uri) != null
        } catch (e: FileNotFoundException) {
            false
        } catch (e: Exception) {
            true
        }

    private fun displayName(uri: Uri): String? =
        context.contentResolver
            .query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }

    /** Null when the provider cannot rename, or refuses: the caller rolls back either way. */
    private fun renameDocument(uri: Uri, name: String): Uri? =
        try {
            DocumentsContract.renameDocument(context.contentResolver, uri, name)
        } catch (e: Exception) {
            null
        }

    private fun parentOf(uri: Uri): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null

        val ids = try {
            DocumentsContract.findDocumentPath(context.contentResolver, uri)?.path
        } catch (e: Exception) {
            null
        } ?: return null

        return ids.getOrNull(ids.size - 2)
            ?.let { DocumentsContract.buildDocumentUriUsingTree(uri, it) }
    }

    private fun childNamed(parent: Uri, name: String): Uri? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            parent,
            DocumentsContract.getDocumentId(parent),
        )

        context.contentResolver
            .query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null,
                null,
                null,
            )
            ?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == name) {
                        return DocumentsContract.buildDocumentUriUsingTree(
                            parent,
                            cursor.getString(0)
                        )
                    }
                }
            }

        return null
    }

    private companion object {
        const val AsideSuffix = ".fserver-replaced"
    }
}
