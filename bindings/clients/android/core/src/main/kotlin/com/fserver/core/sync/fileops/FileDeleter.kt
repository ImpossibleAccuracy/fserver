package com.fserver.core.sync.fileops

import com.fserver.core.crypto.internal.SourceFileSystems
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.index.LocalIndexWriter
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.index.toFileRecord
import com.fserver.core.sync.model.SourceEntry
import com.fserver.files.upload.FileRecord
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Deletes a file's local bytes and records the deletion, for our own plan or the peer's. */
internal class FileDeleter(
    private val storage: FServerStorage,
    private val sourceFiles: SourceFileSystems,
    private val indexWriter: LocalIndexWriter,
) {
    /**
     * Records the deletion as [version], or as a new version of our own when null. Refused when the
     * file is no longer what [expected] says: deleting over an edit the plan never saw destroys it.
     *
     * @return whether the file is gone
     */
    suspend fun delete(
        source: SourceEntry,
        key: IndexedFileKey,
        version: LocalIndexedFile.Version?,
        expected: FileRecord? = null,
    ): Boolean {
        val row = storage.index.findFile(key)
        if (row == null || (expected != null && !row.toFileRecord().sameAs(expected))) {
            Timber.w("Not deleting ${key.fileId} in source ${source.id}: it changed since planned")
            return false
        }

        return withContext(NonCancellable) {
            val fs = sourceFiles.open(source)
            // Nothing there is as good as deleted.
            val deleted = fs.openFile(row.locator)?.delete() ?: true

            if (!deleted) {
                Timber.w("Failed to delete ${row.path} from source ${source.id}")
                return@withContext false
            }

            indexWriter.recordDeleted(source, key, version)
            true
        }
    }
}

/** Same kind of state, content and version: nothing happened to the file in between. */
private fun FileRecord.sameAs(other: FileRecord): Boolean =
    state::class == other.state::class && content == other.content && metadata.version == other.metadata.version
