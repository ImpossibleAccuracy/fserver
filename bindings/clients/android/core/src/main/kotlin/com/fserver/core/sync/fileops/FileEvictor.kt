package com.fserver.core.sync.fileops

import com.fserver.common.model.ContentHash
import com.fserver.core.files.scan.toFiles
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.index.LocalIndexWriter
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.model.SourceEntry
import com.fserver.files.FilesNode
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Frees a file's local bytes, keeping it in the set. The one place eviction happens. */
internal class FileEvictor(
    private val storage: FServerStorage,
    private val node: FilesNode,
    private val indexWriter: LocalIndexWriter,
) {
    /**
     * Evicts [fileId] only while it is still unpinned and holds [expected], and the peer confirmed
     * the same bytes. Returns whether it did.
     */
    suspend fun evict(source: SourceEntry, fileId: String, expected: ContentHash?): Boolean {
        val key = IndexedFileKey(fileId = fileId, sourceId = source.id)
        val row = storage.index.findFile(key)

        val unchanged = row != null && expected != null && !row.hashStale && row.hash == expected &&
                (row.state as? LocalIndexedFile.State.Present)?.pinned == false
        if (!unchanged) {
            Timber.w("Not evicting $fileId in source ${source.id}: it changed since planned")
            return false
        }

        // Only once the peer confirmed the same bytes.
        val copy = storage.remoteIndex.files(source.id).find { it.fileId == fileId }
        if (copy == null || copy.state !is LocalIndexedFile.State.Present || copy.hash != expected) {
            Timber.w("Not evicting ${row.path} in source ${source.id}: peer holds no confirmed copy")
            return false
        }

        return withContext(NonCancellable) {
            val fs = node.openSource(source.location.toFiles())
            // Nothing there is as good as deleted.
            val deleted = fs.openFile(row.locator)?.delete() ?: true

            if (!deleted) {
                Timber.w("Failed to evict ${row.path} from source ${source.id}")
                return@withContext false
            }

            indexWriter.recordEvicted(source, key)
            true
        }
    }
}
