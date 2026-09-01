package com.fserver.core.store.sync

import com.fserver.common.model.ContentHash
import com.fserver.core.store.FServerStorageApi
import com.fserver.core.sync.index.IndexedFile
import com.fserver.core.sync.index.IndexedFileKey

/**
 * What each source has already worked through. The sources themselves live on [SourcesStore].
 *
 * Every record here is bookkeeping about a file the engine already handed off - never a claim
 * about bytes on disk. See [com.fserver.core.store.FServerStorageApi].
 */
@SubclassOptInRequired(FServerStorageApi::class)
interface FileIndexStore {
    suspend fun findFile(key: IndexedFileKey): IndexedFile?

    /**
     * Everything [sourceId] has already handed off, so the next pass can diff against it.
     *
     * Read once per pass and compared against the whole scan, so it must tolerate being large.
     */
    suspend fun processedFiles(sourceId: String): List<IndexedFile>

    /**
     * Records [indexed] as done, replacing any earlier record with the same [IndexedFile.id].
     *
     * Call this only once a file has actually been handed off. Marking ahead of the handoff means
     * a failure leaves the file permanently skipped.
     */
    suspend fun markProcessed(indexed: Collection<IndexedFile>)

    /** Records that [key] has the given [hash], replacing any earlier record. */
    suspend fun saveHash(key: IndexedFileKey, hash: ContentHash)

    suspend fun updateFileState(key: IndexedFileKey, state: IndexedFile.State)

    suspend fun updateStateBatch(keys: List<IndexedFileKey>, state: IndexedFile.State)

    /**
     * Forgets what [sourceId] has done, so the next pass treats every file as new. Touches no
     * bytes on disk: this is bookkeeping, never an eviction or deletion.
     */
    suspend fun clearProcessed(sourceId: String)
}
