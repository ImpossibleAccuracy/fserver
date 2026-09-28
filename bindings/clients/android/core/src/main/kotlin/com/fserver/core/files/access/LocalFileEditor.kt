package com.fserver.core.files.access

import com.fserver.common.exception.FileSystemException
import com.fserver.common.exception.SyncException
import com.fserver.common.model.FileSize
import com.fserver.common.utils.SourcePaths
import com.fserver.core.files.ensureSourceReachable
import com.fserver.core.files.scan.toFiles
import com.fserver.core.requirement.RequirementsChecker
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.index.LocalIndexWriter
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.drivesSync
import com.fserver.core.util.TimeProvider
import com.fserver.files.FilesNode
import com.fserver.files.fs.FileSystem
import com.fserver.files.fs.FsWriter
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * The user's own changes to a source's files: the bytes, then the index as a new version of ours,
 * so the next pass sends them on. Only where this device drives the source - see [drivesSync].
 */
internal class LocalFileEditor(
    private val storage: FServerStorage,
    private val node: FilesNode,
    private val indexWriter: LocalIndexWriter,
    private val requirementsChecker: RequirementsChecker,
    private val timeProvider: TimeProvider,
) {
    suspend fun find(sourceId: String, fileId: String): SourceFile? =
        storage.index.findFile(IndexedFileKey(fileId = fileId, sourceId = sourceId))
            ?.takeUnless { it.isDeleted }
            ?.let { SourceFile(this, it) }

    suspend fun create(sourceId: String, path: String): SourceFile {
        val source = writableSource(sourceId)
        val canonical = SourcePaths.canonical(null, path)
        val key = IndexedFileKey(fileId = SourcePaths.fileId(canonical), sourceId = sourceId)

        val fs = open(source)
        fs.checkPath(canonical)
        if (storage.index.findFile(key)?.state is LocalIndexedFile.State.Present || fs.fileExists(canonical)) {
            throw FileSystemException.AlreadyExists(canonical)
        }

        return withContext(NonCancellable) {
            val file = fs.createFile(canonical)
            val modifiedAt = file.settleLastModified(timeProvider.now())
            SourceFile(this@LocalFileEditor, indexWriter.recordCreated(source, canonical, file.locator, modifiedAt))
        }
    }

    suspend fun read(key: IndexedFileKey): InputStream {
        val source = source(key.sourceId)
        val row = present(key)
        return open(source).openFile(row.locator)?.read() ?: throw FileNotFoundException(row.path)
    }

    suspend fun rename(key: IndexedFileKey, newName: String): SourceFile {
        require(newName.isNotBlank() && newName.none { it == '/' || it == '\\' }) { "Not a file name: $newName" }

        val source = writableSource(key.sourceId)
        val row = present(key)
        val path = SourcePaths.canonical(null, listOf(row.path.substringBeforeLast('/', ""), newName))
        if (path == row.path) return SourceFile(this, row)

        val target = IndexedFileKey(fileId = SourcePaths.fileId(path), sourceId = source.id)
        if (storage.index.findFile(target)?.state is LocalIndexedFile.State.Present) {
            throw FileSystemException.AlreadyExists(path)
        }

        val file = open(source).openFile(row.locator) ?: throw FileNotFoundException(row.path)
        return withContext(NonCancellable) {
            // Refuses when a file not indexed yet sits under the new name.
            val renamed = file.rename(path.substringAfterLast('/'))
            val modifiedAt = renamed.settleLastModified(row.modifiedAt)
            SourceFile(this@LocalFileEditor, indexWriter.recordRenamed(source, key, path, renamed.locator, modifiedAt))
        }
    }

    /** An evicted file has no bytes here, and is only recorded deleted. */
    suspend fun delete(key: IndexedFileKey) {
        val source = writableSource(key.sourceId)
        val row = storage.index.findFile(key)?.takeUnless { it.isDeleted } ?: return

        withContext(NonCancellable) {
            if (row.state is LocalIndexedFile.State.Present) {
                // Nothing there is as good as deleted.
                val deleted = open(source).openFile(row.locator)?.delete() ?: true
                if (!deleted) throw FileSystemException.DeleteRejected(row.locator)
            }

            indexWriter.recordDeleted(source, key, version = null)
        }
    }

    suspend fun write(key: IndexedFileKey, block: suspend (SourceFileWriter) -> Unit): SourceFile {
        val source = writableSource(key.sourceId)
        val row = present(key)
        val file = open(source).openFile(row.locator) ?: throw FileNotFoundException(row.path)

        val writer = TrackingWriter(file.openWriter())
        var written = row
        try {
            writer.use {
                block(it)
                it.sync()
            }
        } finally {
            // Recorded even when the block failed: whatever it wrote is on disk already.
            if (writer.touched) withContext(NonCancellable) {
                written = indexWriter.recordWritten(
                    source = source,
                    key = key,
                    size = FileSize(maxOf(row.size.bytes, writer.end)),
                    modifiedAt = file.settleLastModified(timeProvider.now()),
                )
            }
        }

        return SourceFile(this, written)
    }

    private suspend fun present(key: IndexedFileKey): LocalIndexedFile =
        storage.index.findFile(key)?.takeIf { it.state is LocalIndexedFile.State.Present }
            ?: throw FileNotFoundException("File ${key.fileId} in source ${key.sourceId} is not held here")

    private suspend fun source(sourceId: String): SourceEntry {
        val source = storage.sources.findById(sourceId)
            ?: throw IllegalArgumentException("Source $sourceId is not registered")
        requirementsChecker.ensureSourceReachable(source.location)
        return source
    }

    private suspend fun writableSource(sourceId: String): SourceEntry = source(sourceId).also {
        if (!it.drivesSync) {
            throw SyncException.ModeForbiddenException("Source $sourceId is driven by its peer: its files are read-only here")
        }
    }

    private fun open(source: SourceEntry): FileSystem = node.openSource(source.location.toFiles())
}

/** Remembers how far the writes reached, since [FsWriter] cannot tell the size. */
private class TrackingWriter(private val delegate: FsWriter) : SourceFileWriter, AutoCloseable {
    var end = 0L
        private set
    var touched = false
        private set

    override suspend fun write(offset: Long, bytes: ByteArray, length: Int) {
        touched = true
        delegate.write(offset, bytes, length)
        end = maxOf(end, offset + length)
    }

    suspend fun sync() = delegate.sync()

    override fun close() = delegate.close()
}
