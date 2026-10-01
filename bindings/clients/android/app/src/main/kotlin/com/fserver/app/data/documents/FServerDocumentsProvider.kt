package com.fserver.app.data.documents

import android.app.Application
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.HandlerThread
import android.os.OperationCanceledException
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.system.ErrnoException
import android.system.OsConstants
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import com.fserver.app.R
import com.fserver.app.di.AppGraph
import com.fserver.app.domain.documents.DocumentIds
import com.fserver.app.domain.documents.DocumentNode
import com.fserver.app.domain.documents.DocumentsRepository
import com.fserver.app.domain.documents.OwnDocumentsAuthority
import com.fserver.app.domain.documents.OpenedDocument
import com.fserver.app.presentation.composable.model.fileExtension
import com.fserver.core.files.access.SourceFileReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

/** Every source as a read-only root of the system file picker. See [DocumentsRepository]. */
class FServerDocumentsProvider : DocumentsProvider(), KoinComponent {
    // Resolved on first call, which may come before `Application.onCreate` has started Koin.
    private val documents: DocumentsRepository by lazy { started { get() } }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val watching = AtomicBoolean(false)

    /** Serves the reads of every proxied descriptor. */
    private val ioHandler by lazy { Handler(HandlerThread("documents-io").apply { start() }.looper) }

    private val ctx: Context get() = checkNotNull(context) { "Provider is not attached" }

    override fun onCreate(): Boolean = true

    private inline fun <T> started(resolve: () -> T): T {
        AppGraph.start(ctx.applicationContext as Application)
        return resolve()
    }

    override fun queryRoots(projection: Array<String>?): Cursor {
        watchChanges()

        val cursor = MatrixCursor(projection ?: DefaultRootProjection)
        runBlocking { documents.sources() }.forEach { source ->
            cursor.newRow()
                .add(Root.COLUMN_ROOT_ID, source.id)
                .add(Root.COLUMN_DOCUMENT_ID, DocumentIds.of(source.id, path = ""))
                .add(Root.COLUMN_TITLE, source.label)
                .add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_IS_CHILD)
                .add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
        }
        cursor.setNotificationUri(ctx.contentResolver, DocumentsContract.buildRootsUri(OwnDocumentsAuthority))
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<String>?): Cursor {
        watchChanges()

        val sourceId = DocumentIds.sourceOf(documentId)
        val path = DocumentIds.pathOf(documentId)
        val (source, node) = runBlocking {
            documents.source(sourceId) to documents.node(
                sourceId,
                path
            )
        }
        if (source == null || node == null) throw FileNotFoundException(documentId)

        val cursor = MatrixCursor(projection ?: DefaultDocumentProjection)
        cursor.addNode(sourceId, node, name = if (path.isEmpty()) source.label else node.name)
        cursor.setNotificationUri(
            ctx.contentResolver,
            DocumentsContract.buildDocumentUri(OwnDocumentsAuthority, documentId)
        )
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<String>?,
        sortOrder: String?,
    ): Cursor {
        watchChanges()

        val sourceId = DocumentIds.sourceOf(parentDocumentId)
        val children =
            runBlocking { documents.children(sourceId, DocumentIds.pathOf(parentDocumentId)) }
                ?: throw FileNotFoundException(parentDocumentId)

        val cursor = MatrixCursor(projection ?: DefaultDocumentProjection)
        children.forEach { cursor.addNode(sourceId, it, it.name) }
        cursor.setNotificationUri(
            ctx.contentResolver,
            DocumentsContract.buildChildDocumentsUri(OwnDocumentsAuthority, parentDocumentId),
        )
        return cursor
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        DocumentIds.isChild(parentDocumentId, documentId)

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        if (mode != "r") throw UnsupportedOperationException("Read-only provider, asked for mode $mode")

        val file = file(documentId)
        val opened = try {
            cancellable(signal) { documents.open(file) }
        } catch (e: OperationCanceledException) {
            throw e
        } catch (e: Exception) {
            throw FileNotFoundException("Cannot open $documentId: ${e.message}")
        }

        return when (opened) {
            // The real descriptor where there is one: some apps name the file after its path.
            is OpenedDocument.Descriptor -> opened.descriptor
            is OpenedDocument.Reader -> proxy(opened.reader)
        }
    }

    private fun proxy(reader: SourceFileReader): ParcelFileDescriptor =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.getSystemService(StorageManager::class.java)
                .openProxyFileDescriptor(ParcelFileDescriptor.MODE_READ_ONLY, ReaderCallback(reader), ioHandler)
        } else {
            pipe(reader)
        }

    override fun openDocumentThumbnail(
        documentId: String,
        sizeHint: Point,
        signal: CancellationSignal?,
    ): AssetFileDescriptor {
        val file = file(documentId)
        val bitmap = cancellable(signal) { documents.thumbnail(file, sizeHint.x, sizeHint.y) }
            ?: throw FileNotFoundException("No thumbnail for $documentId")

        // Unlinked once open: the descriptor keeps the bytes, nothing is left behind in the cache.
        val temp = File.createTempFile("thumb", ".jpg", ctx.cacheDir)
        try {
            temp.outputStream()
                .use { bitmap.compress(Bitmap.CompressFormat.JPEG, ThumbnailQuality, it) }
            val descriptor = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
            return AssetFileDescriptor(descriptor, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
        } finally {
            temp.delete()
        }
    }

    private fun file(documentId: String): DocumentNode.File {
        val node = runBlocking {
            documents.node(
                DocumentIds.sourceOf(documentId),
                DocumentIds.pathOf(documentId)
            )
        }
        return node as? DocumentNode.File ?: throw FileNotFoundException(documentId)
    }

    /** API 24-25 have no proxy descriptor: the bytes stream through a pipe, and the reader cannot seek. */
    private fun pipe(reader: SourceFileReader): ParcelFileDescriptor {
        val (read, write) = ParcelFileDescriptor.createReliablePipe()
        scope.launch {
            try {
                reader.use {
                    ParcelFileDescriptor.AutoCloseOutputStream(write).use { output ->
                        val buffer = ByteArray(PipeBufferSize)
                        var offset = 0L
                        while (true) {
                            val count = reader.read(offset, buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            offset += count
                        }
                    }
                }
            } catch (e: IOException) {
                Timber.w(e, "Pipe to a document reader broke")
                write.closeWithError(e.message)
            }
        }
        return read
    }

    private fun MatrixCursor.addNode(sourceId: String, node: DocumentNode, name: String) {
        val row = newRow()
            .add(Document.COLUMN_DOCUMENT_ID, DocumentIds.of(sourceId, node.path))
            .add(Document.COLUMN_DISPLAY_NAME, name)

        when (node) {
            is DocumentNode.Folder -> row
                .add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
                .add(Document.COLUMN_FLAGS, 0)

            is DocumentNode.File -> row
                .add(Document.COLUMN_MIME_TYPE, mimeTypeOf(node.name))
                .add(Document.COLUMN_SIZE, node.entry.size.bytes)
                .add(Document.COLUMN_LAST_MODIFIED, node.entry.modifiedAt.toEpochMilliseconds())
                .add(
                    Document.COLUMN_FLAGS,
                    if (documents.hasThumbnail(node)) Document.FLAG_SUPPORTS_THUMBNAIL else 0
                )
        }
    }

    /** Starts telling open cursors about changes, once per process. */
    @OptIn(FlowPreview::class)
    private fun watchChanges() {
        if (!watching.compareAndSet(false, true)) return

        // Every document uri sits below this one, so one notification reaches every open cursor.
        val everything = Uri.Builder().scheme("content").authority(OwnDocumentsAuthority).build()
        scope.launch {
            documents.changes
                .debounce(NotifyDebounce)
                .collect { ctx.contentResolver.notifyChange(everything, null) }
        }
    }

    private companion object {
        const val ThumbnailQuality = 85
        const val PipeBufferSize = 256 * 1024
        val NotifyDebounce = 300.milliseconds

        val DefaultRootProjection = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_TITLE,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
        )

        val DefaultDocumentProjection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
        )
    }
}

/** A seekable descriptor over [reader]: the system calls back here for every read the client makes. */
@RequiresApi(Build.VERSION_CODES.O)
private class ReaderCallback(private val reader: SourceFileReader) : ProxyFileDescriptorCallback() {
    override fun onGetSize(): Long = io { reader.size() }

    /** Fills [data] up to [size]: a short read reads as the end of the file. */
    override fun onRead(offset: Long, size: Int, data: ByteArray): Int = io {
        var total = 0
        while (total < size) {
            val chunk = if (total == 0) data else ByteArray(size - total)
            val count = reader.read(offset + total, chunk, size - total)
            if (count <= 0) break

            if (chunk !== data) chunk.copyInto(data, destinationOffset = total, endIndex = count)
            total += count
        }
        total
    }

    override fun onRelease() = reader.close()

    private fun <T> io(block: suspend () -> T): T = try {
        runBlocking { block() }
    } catch (e: IOException) {
        throw ErrnoException("read", OsConstants.EIO, e)
    }
}

/** Runs [block] on the binder thread that asked, abandoning it when [signal] is cancelled. */
private fun <T> cancellable(signal: CancellationSignal?, block: suspend () -> T): T = runBlocking {
    val job = async { block() }
    signal?.setOnCancelListener { job.cancel() }
    try {
        job.await()
    } catch (e: CancellationException) {
        throw OperationCanceledException(e.message)
    }
}

private fun mimeTypeOf(name: String): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.fileExtension) ?: FallbackMimeType

private const val FallbackMimeType = "application/octet-stream"
