package com.fserver.core.sync.fileops

import com.fserver.common.model.ContentHash
import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.files.preview.EvictingFile
import com.fserver.core.files.preview.EvictionPreviewer
import com.fserver.core.files.scan.toFiles
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.index.LocalIndexWriter
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.model.SourceEntry
import com.fserver.files.FilesNode
import com.fserver.files.fs.FsFile
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

/** Frees a file's local bytes, keeping it in the set. The one place eviction happens. */
internal class FileEvictor(
    private val storage: FServerStorage,
    private val node: FilesNode,
    private val indexWriter: LocalIndexWriter,
    private val previewer: EvictionPreviewer? = null,
) {
    /**
     * Evicts [fileId] only while it is still unpinned and holds [expected], and the peer confirmed
     * the same bytes. Returns whether it did.
     */
    suspend fun evict(source: SourceEntry, fileId: String, expected: ContentHash?): Boolean {
        val key = IndexedFileKey(fileId = fileId, sourceId = source.id)
        val row = storage.index.findFile(key)

        val unchanged = row != null && !row.hashStale && row.hash == expected &&
                (row.state as? LocalIndexedFile.State.Present)?.pinned == false
        if (expected == null || !unchanged) {
            Timber.w("Not evicting $fileId in source ${source.id}: it changed since planned")
            return false
        }

        // Only once the peer confirmed the same bytes.
        val copy = storage.remoteIndex.files(source.id).find { it.fileId == fileId }
        if (copy == null || copy.state !is LocalIndexedFile.State.Present || copy.hash != expected) {
            Timber.w("Not evicting ${row.path} in source ${source.id}: peer holds no confirmed copy")
            return false
        }

        // Before the NonCancellable part: a cancelled pass should not sit out a slow preview.
        val file = node.openSource(source.location.toFiles()).openFile(row.locator)
        if (file != null) capturePreview(source, row, expected, file)

        return withContext(NonCancellable) {
            // Nothing there is as good as deleted.
            val deleted = file?.delete() ?: true

            if (!deleted) {
                Timber.w("Failed to evict ${row.path} from source ${source.id}")
                return@withContext false
            }

            indexWriter.recordEvicted(source, key)
            true
        }
    }

    private suspend fun capturePreview(source: SourceEntry, row: LocalIndexedFile, hash: ContentHash, file: FsFile) {
        val previewer = previewer ?: return
        val evicting = EvictingFile(
            sourceId = source.id,
            fileId = row.fileId,
            path = row.path,
            size = row.size,
            hash = hash,
            locator = file.locator,
            open = file::read,
        )

        runCatchingCancellable { withTimeoutOrNull(PreviewTimeout) { previewer.capture(evicting) } }
            .onSuccess { if (it == null) Timber.w("Preview of ${row.path} timed out, evicting without one") }
            .onFailure { Timber.w(it, "Preview of ${row.path} failed, evicting without one") }
    }

    private companion object {
        /** A stuck decoder must not hold eviction up: freeing space matters more than a picture. */
        val PreviewTimeout = 30.seconds
    }
}
