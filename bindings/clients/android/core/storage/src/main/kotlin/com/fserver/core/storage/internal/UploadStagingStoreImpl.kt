package com.fserver.core.storage.internal

import com.fserver.core.storage.database.FServerStorageDatabase
import com.fserver.core.store.sync.UploadStagingStore
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.model.StagedUpload
import kotlin.time.Instant
import com.fserver.core.storage.database.StagedUpload as DBStagedUpload

internal class UploadStagingStoreImpl(
    database: FServerStorageDatabase,
) : UploadStagingStore {
    private val dao = database.stagedUploadQueries

    override suspend fun all(): List<StagedUpload> =
        dao.selectAll().executeAsList().map { it.toDomainModel() }

    override suspend fun find(key: IndexedFileKey): StagedUpload? =
        dao.selectByKey(key.sourceId, key.fileId).executeAsOneOrNull()?.toDomainModel()

    override suspend fun upsert(upload: StagedUpload) {
        dao.upsert(
            sourceId = upload.sourceId,
            fileId = upload.fileId,
            deviceId = upload.deviceId,
            locator = upload.locator,
            size = upload.size,
            modifiedAtEpochMs = upload.modifiedAt.toEpochMilliseconds(),
            versionHlc = upload.versionHlc,
            versionOrigin = upload.versionOrigin,
            committedOffset = upload.committedOffset,
            startedAtEpochMs = upload.startedAt.toEpochMilliseconds(),
            touchedAtEpochMs = upload.touchedAt.toEpochMilliseconds(),
        )
    }

    override suspend fun checkpoint(key: IndexedFileKey, offset: Long, at: Instant) {
        dao.checkpoint(
            committedOffset = offset,
            touchedAtEpochMs = at.toEpochMilliseconds(),
            sourceId = key.sourceId,
            fileId = key.fileId,
        )
    }

    override suspend fun delete(key: IndexedFileKey) {
        dao.delete(sourceId = key.sourceId, fileId = key.fileId)
    }
}

private fun DBStagedUpload.toDomainModel() = StagedUpload(
    sourceId = sourceId,
    fileId = fileId,
    deviceId = deviceId,
    locator = locator,
    size = size,
    modifiedAt = Instant.fromEpochMilliseconds(modifiedAtEpochMs),
    versionHlc = versionHlc,
    versionOrigin = versionOrigin,
    committedOffset = committedOffset,
    startedAt = Instant.fromEpochMilliseconds(startedAtEpochMs),
    touchedAt = Instant.fromEpochMilliseconds(touchedAtEpochMs),
)
