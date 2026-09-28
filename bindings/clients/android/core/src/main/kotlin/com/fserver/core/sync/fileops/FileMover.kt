package com.fserver.core.sync.fileops

import com.fserver.common.model.ContentHash
import com.fserver.core.files.scan.toFiles
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.index.LocalIndexWriter
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.index.toIndexed
import com.fserver.core.sync.model.SourceEntry
import com.fserver.files.FilesNode
import com.fserver.files.upload.FileRecord
import com.fserver.files.upload.FileVersion
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.FileNotFoundException

/** Carries out a planned rename on this device, for our own plan or the peer's. */
internal class FileMover(
    private val storage: FServerStorage,
    private val node: FilesNode,
    private val indexWriter: LocalIndexWriter,
) {
    /**
     * Moves the bytes of [from] to [target]'s path, recording [target] under [version] and [from]'s
     * deletion under [deletedVersion]. Refused when [from] no longer holds [expected], or something
     * already sits at the target: the plan never saw it, and placing over it would destroy it.
     */
    suspend fun move(
        source: SourceEntry,
        from: IndexedFileKey,
        expected: ContentHash,
        target: FileRecord,
        version: FileVersion?,
        deletedVersion: FileVersion?,
    ) {
        val row = storage.index.findFile(from)
        check(row != null && row.state is LocalIndexedFile.State.Present && !row.hashStale && row.hash == expected) {
            "File ${from.fileId} in source ${source.id} changed since the move was planned"
        }

        val targetKey = IndexedFileKey(fileId = target.id.value, sourceId = source.id)
        check(storage.index.findFile(targetKey)?.state !is LocalIndexedFile.State.Present) {
            "${target.path} in source ${source.id} is already held"
        }

        withContext(NonCancellable) {
            val fs = node.openSource(source.location.toFiles())
            check(!fs.fileExists(target.path)) { "${target.path} in source ${source.id} is taken by a file not indexed yet" }

            val file = fs.openFile(row.locator) ?: throw FileNotFoundException(row.locator)
            val placed = fs.place(file, target.path)

            // Recorded as the disk reports it, or the next scan reads a mismatch as a local edit.
            val modifiedAt = placed.settleLastModified(target.metadata.lastModified)

            indexWriter.recordMoved(
                source = source,
                from = from,
                deletedVersion = deletedVersion?.toIndexed(),
                fileId = target.id.value,
                path = target.path,
                locator = placed.locator,
                modifiedAt = modifiedAt,
                version = version?.toIndexed(),
            )
        }

        Timber.i("Moved ${row.path} to ${target.path} in source ${source.id}")
    }
}
