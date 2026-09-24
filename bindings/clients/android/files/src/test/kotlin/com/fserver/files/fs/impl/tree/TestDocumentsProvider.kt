package com.fserver.files.fs.impl.tree

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.FileNotFoundException

/**
 * A document tree backed by a directory in the cache, for [TreeFileSystemTest].
 *
 * Real enough to be worth testing against: every call still goes through `DocumentsContract`, so
 * the tree-uri plumbing, the child checks and the document ids are the framework's, not a fake's.
 */
class TestDocumentsProvider : DocumentsProvider() {

    private val root: File
        get() = rootDirectory(requireNotNull(context))

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DefaultRootProjection)

        cursor.newRow()
            .add(DocumentsContract.Root.COLUMN_ROOT_ID, RootDocumentId)
            .add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, RootDocumentId)
            .add(DocumentsContract.Root.COLUMN_TITLE, RootDirectory)
            .add(DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.FLAG_SUPPORTS_CREATE)

        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val file = fileOf(documentId)
        if (!file.exists()) throw FileNotFoundException(documentId)

        return MatrixCursor(projection ?: DefaultDocumentProjection).also {
            addRow(it, file, documentId)
        }
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val cursor = MatrixCursor(projection ?: DefaultDocumentProjection)

        fileOf(parentDocumentId).listFiles().orEmpty().forEach { child ->
            addRow(cursor, child, documentIdOf(child))
        }

        return cursor
    }

    /** Without this the framework refuses every call made through a tree uri. */
    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        documentId == parentDocumentId || documentId.startsWith("$parentDocumentId/")

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String,
    ): String {
        val target = File(fileOf(parentDocumentId), displayName)

        val created = if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
            target.mkdirs()
        } else {
            target.parentFile?.mkdirs()
            target.createNewFile()
        }

        if (!created) throw FileNotFoundException(displayName)

        return documentIdOf(target)
    }

    override fun deleteDocument(documentId: String) {
        if (!fileOf(documentId).deleteRecursively()) throw FileNotFoundException(documentId)
    }

    /** A name already taken is refused, as a provider that does not pick a free one would. */
    override fun renameDocument(documentId: String, displayName: String): String {
        val file = fileOf(documentId)
        val target = File(file.parentFile, displayName)

        if (target.exists() || !file.renameTo(target)) throw FileNotFoundException(displayName)

        return documentIdOf(target)
    }

    /** Ids from [parentDocumentId] - or the root - down to the document; what `TreeFile` finds a parent by. */
    override fun findDocumentPath(parentDocumentId: String?, childDocumentId: String): DocumentsContract.Path {
        val ids = generateSequence(fileOf(childDocumentId)) { if (it == root) null else it.parentFile }
            .map(::documentIdOf)
            .toList()
            .reversed()

        val from = parentDocumentId?.let { ids.indexOf(it).coerceAtLeast(0) } ?: 0

        return DocumentsContract.Path(if (parentDocumentId == null) RootDocumentId else null, ids.drop(from))
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor =
        ParcelFileDescriptor.open(fileOf(documentId), ParcelFileDescriptor.parseMode(mode))

    /**
     * Fills whatever columns were asked for, in the order they were asked for: the tree scanner
     * reads its cursor by column index, so a provider that ignored the projection would hide that.
     */
    private fun addRow(cursor: MatrixCursor, file: File, documentId: String) {
        val row = cursor.newRow()

        for (column in cursor.columnNames) {
            row.add(column, valueOf(column, file, documentId))
        }
    }

    private fun valueOf(column: String, file: File, documentId: String): Any? = when (column) {
        DocumentsContract.Document.COLUMN_DOCUMENT_ID -> documentId
        DocumentsContract.Document.COLUMN_DISPLAY_NAME ->
            if (documentId == RootDocumentId) RootDirectory else file.name

        DocumentsContract.Document.COLUMN_MIME_TYPE ->
            if (file.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else FileMimeType

        DocumentsContract.Document.COLUMN_SIZE -> file.length()
        DocumentsContract.Document.COLUMN_LAST_MODIFIED -> file.lastModified()
        DocumentsContract.Document.COLUMN_FLAGS -> DocumentFlags
        else -> null
    }

    private fun fileOf(documentId: String): File {
        val relative = documentId.removePrefix(RootDocumentId).trimStart('/')

        return if (relative.isEmpty()) root else File(root, relative)
    }

    private fun documentIdOf(file: File): String {
        val relative = file.relativeTo(root).invariantSeparatorsPath

        return if (relative.isEmpty()) RootDocumentId else "$RootDocumentId/$relative"
    }

    companion object {
        const val Authority = "com.fserver.files.test.documents"

        private const val RootDocumentId = "root"
        private const val RootDirectory = "tree-root"
        private const val FileMimeType = "application/octet-stream"

        private const val DocumentFlags = DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE or
            DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
            DocumentsContract.Document.FLAG_SUPPORTS_RENAME or
            DocumentsContract.Document.FLAG_SUPPORTS_WRITE

        private val DefaultRootProjection = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_FLAGS,
        )

        private val DefaultDocumentProjection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
        )

        /**
         * Registers the provider with Robolectric's resolver. `attachInfo` refuses a provider that
         * is not exported, grant-capable and behind MANAGE_DOCUMENTS, as the manifest would declare it.
         */
        fun register() {
            val info = ProviderInfo().apply {
                authority = Authority
                exported = true
                grantUriPermissions = true
                readPermission = android.Manifest.permission.MANAGE_DOCUMENTS
                writePermission = android.Manifest.permission.MANAGE_DOCUMENTS
            }

            val provider = Robolectric.buildContentProvider(TestDocumentsProvider::class.java)
                .create(info)
                .get()

            val bridge = Bridge(provider).apply {
                attachInfo(RuntimeEnvironment.getApplication(), ProviderInfo().also { it.authority = Authority })
            }

            ShadowContentResolver.registerProviderInternal(Authority, bridge)
        }

        /** The tree uri a picker would have handed back for this provider's root. */
        fun treeUri(): Uri = DocumentsContract.buildTreeDocumentUri(Authority, RootDocumentId)

        /** Where the tree actually lives, so a test can set it up and assert on it directly. */
        fun rootDirectory(context: Context): File = File(context.cacheDir, RootDirectory)
    }

    /**
     * Robolectric's resolver still calls the pre-O `query`, which [DocumentsProvider] seals off with
     * a throw. This forwards it to the bundle overload the framework would call.
     */
    private class Bridge(private val target: DocumentsProvider) : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = target.query(uri, projection, null as Bundle?, null)

        override fun call(method: String, arg: String?, extras: Bundle?): Bundle? =
            target.call(method, arg, extras)

        override fun call(authority: String, method: String, arg: String?, extras: Bundle?): Bundle? =
            target.call(authority, method, arg, extras)

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? =
            target.openFile(uri, mode)

        override fun getType(uri: Uri): String? = target.getType(uri)

        override fun insert(uri: Uri, values: ContentValues?): Uri? =
            throw UnsupportedOperationException()

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = throw UnsupportedOperationException()

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
            throw UnsupportedOperationException()
    }
}
