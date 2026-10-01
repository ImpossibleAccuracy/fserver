package com.fserver.app.domain.documents

import android.graphics.Bitmap
import com.fserver.core.sync.model.SourceEntry
import kotlinx.coroutines.flow.Flow

/**
 * Sources as other apps browse them: read-only, one tree per source. A file only the peer holds
 * is shown where its source fetches on demand.
 */
interface DocumentsRepository {
    /** Emits whenever a source, or a file any of them shows, changes. */
    val changes: Flow<Unit>

    suspend fun sources(): List<SourceEntry>

    suspend fun source(sourceId: String): SourceEntry?

    /** The node at [path] of [sourceId] - empty for its root - or null when nothing shown is there. */
    suspend fun node(sourceId: String, path: String): DocumentNode?

    /** Direct children of folder [path] of [sourceId], or null when the source is not registered. */
    suspend fun children(sourceId: String, path: String): List<DocumentNode>?

    fun hasThumbnail(file: DocumentNode.File): Boolean

    /** Opens [file] for reading, fetching it from the peer first when only the peer holds it. */
    suspend fun open(file: DocumentNode.File): OpenedDocument

    /** A picture of [file] at about [width] x [height], or null when there is none. */
    suspend fun thumbnail(file: DocumentNode.File, width: Int, height: Int): Bitmap?
}

