package com.fserver.core.store.sync

import com.fserver.core.store.FServerStorageApi
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.model.StagedUpload
import kotlin.time.Instant

/** Uploads parked in staging, one per (source, file). See [StagedUpload]. */
@SubclassOptInRequired(FServerStorageApi::class)
interface UploadStagingStore {
    suspend fun all(): List<StagedUpload>

    suspend fun find(key: IndexedFileKey): StagedUpload?

    /** Inserts, or replaces the upload staged for the same file. */
    suspend fun upsert(upload: StagedUpload)

    /** Records that `[0, offset)` is flushed. No-op when the upload is gone. */
    suspend fun checkpoint(key: IndexedFileKey, offset: Long, at: Instant)

    suspend fun delete(key: IndexedFileKey)
}
