package com.fserver.app.data.documents

import android.content.Context
import android.graphics.Bitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.fserver.app.data.preview.EvictionPreviews
import com.fserver.app.domain.documents.DocumentNode
import com.fserver.app.domain.documents.DocumentsRepository
import com.fserver.app.domain.documents.OpenedDocument
import com.fserver.app.domain.documents.childrenOf
import com.fserver.app.domain.documents.nodeAt
import com.fserver.app.presentation.composable.model.fileKindOf
import com.fserver.app.presentation.shared.viewer.impl.FileImage
import com.fserver.core.files.FilesController
import com.fserver.core.files.SyncFileEntry
import com.fserver.core.storage.RegisteredSourcesRepository
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.fetches
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import java.io.FileNotFoundException

class DocumentsRepositoryImpl(
    private val context: Context,
    private val files: FilesController,
    private val sources: RegisteredSourcesRepository,
    private val previews: EvictionPreviews,
) : DocumentsRepository {

    override val changes: Flow<Unit> = combine(files.overallContent, sources.sources) { _, _ -> }.drop(1)

    override suspend fun sources(): List<SourceEntry> = sources.sources.first()

    override suspend fun source(sourceId: String): SourceEntry? = sources.observeById(sourceId).first()

    override suspend fun node(sourceId: String, path: String): DocumentNode? {
        val source = source(sourceId) ?: return null
        if (path.isEmpty()) return DocumentNode.Folder(path)

        files.entry(sourceId, path)?.let { return if (source.shows(it)) DocumentNode.File(it) else null }

        // Not a file, so a folder at most: the index holds none, only the files below one imply it.
        return nodeAt(path, shown(source))
    }

    override suspend fun children(sourceId: String, path: String): List<DocumentNode>? {
        val source = source(sourceId) ?: return null
        return childrenOf(path, shown(source))
    }

    override fun hasThumbnail(file: DocumentNode.File): Boolean {
        val entry = file.entry
        return fileKindOf(entry.path).isMedia &&
            (entry.locator != null || previews.fileFor(entry.sourceId, entry.fileId).exists())
    }

    override suspend fun open(file: DocumentNode.File): OpenedDocument {
        val entry = file.entry
        val present = if (entry.isRemote) files.download(entry).getOrThrow() else entry

        val sourceFile = files.file(present.sourceId, present.fileId)
            ?: throw FileNotFoundException("${present.path} is not held here")
        return sourceFile.openDescriptor()?.let { OpenedDocument.Descriptor(it) }
            ?: OpenedDocument.Reader(sourceFile.openReader())
    }

    override suspend fun thumbnail(file: DocumentNode.File, width: Int, height: Int): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(file.entry.imageModel())
            .size(width, height)
            .allowHardware(false)
            .build()

        val result = SingletonImageLoader.get(context).execute(request)
        return (result as? SuccessResult)?.image?.toBitmap()
    }

    private suspend fun shown(source: SourceEntry): List<SyncFileEntry> =
        files.content(source.id).filter { source.shows(it) }
}

/** Held here, or fetchable from the peer under the source's mode. */
private fun SourceEntry.shows(entry: SyncFileEntry): Boolean = !entry.isRemote || fetches(entry.localState)

/** The same Coil model the app's own tiles load, so they share decoders, cache and kept previews. */
private fun SyncFileEntry.imageModel() = FileImage(
    sourceId = sourceId,
    fileId = fileId,
    locator = locator,
    kind = fileKindOf(path),
    version = "${modifiedAt.toEpochMilliseconds()}:${size.bytes}",
)
