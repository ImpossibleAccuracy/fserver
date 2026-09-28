package com.fserver.core.files.access

import com.fserver.common.model.FileSize
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.index.LocalIndexedFile
import java.io.InputStream
import kotlin.time.Instant

/**
 * One indexed file of a source, as of when it was obtained. Every call re-reads the index, so a
 * stale handle refuses rather than acts on what it remembers.
 *
 * Changes are versioned as this device's own and reach the peer on the next pass. They are refused
 * with `SyncException.ModeForbiddenException` where the peer drives the source.
 */
class SourceFile internal constructor(
    private val editor: LocalFileEditor,
    private val row: LocalIndexedFile,
) {
    val sourceId: String get() = row.sourceId
    val fileId: String get() = row.fileId
    val path: String get() = row.path
    val size: FileSize get() = row.size
    val modifiedAt: Instant get() = row.modifiedAt
    val state: LocalIndexedFile.State get() = row.state

    private val key get() = IndexedFileKey(fileId = fileId, sourceId = sourceId)

    /** Only while the bytes are here: an evicted file is downloaded first. */
    suspend fun read(): InputStream = editor.read(key)

    /**
     * Renames the file to [newName] - a name, not a path - within its directory.
     * The file id follows the path, so the returned handle has a new one; this one is stale.
     */
    suspend fun rename(newName: String): SourceFile = editor.rename(key, newName)

    /** Runs [block] over one writer, then records what it wrote - also when it fails midway. */
    suspend fun write(block: suspend (SourceFileWriter) -> Unit): SourceFile = editor.write(key, block)
}
