package com.fserver.core.network.dictionary.dto

import com.fserver.common.model.ContentHash
import com.fserver.common.model.FileSize
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.index.RemoteIndexedFile
import com.fserver.core.sync.index.toIndexed
import com.fserver.core.sync.limits.SourceUsage
import com.fserver.core.sync.metadata.PeerSourceMetadata
import com.fserver.files.upload.FileId
import com.fserver.files.upload.FileRecord
import com.fserver.files.upload.FileVersion
import com.fserver.files.upload.VersionVector
import kotlin.time.Instant

internal fun LocalIndexedFile.toDto(): FileRecordDto = FileRecordDto(
    id = fileId,
    sourceId = sourceId,
    path = path,
    state = state.toDto(),
    // A stale hash may hide an unversioned edit: sent as unknown, so the peer asks for a rehash.
    content = hash?.takeUnless { hashStale }
        ?.let { ContentHashDto(value = it.value, algorithm = it.algorithm) },
    metadata = FileRecordDto.Metadata(
        size = size.bytes,
        lastModified = modifiedAt,
        version = version?.toDto(),
    ),
)

internal fun FileRecord.toDto(
    sourceId: String,
    version: FileVersion? = metadata.version,
): FileRecordDto = FileRecordDto(
    id = id.value,
    sourceId = sourceId,
    path = path,
    state = state.toDto(),
    content = content?.let { ContentHashDto(value = it.value, algorithm = it.algorithm) },
    metadata = FileRecordDto.Metadata(
        size = metadata.size,
        lastModified = metadata.lastModified,
        version = version?.toDto(),
    ),
)

internal fun FileRecordDto.toFileRecord(): FileRecord = FileRecord(
    id = FileId(id),
    path = path,
    locator = null,
    state = state.toDomain(),
    content = content?.let { ContentHash(value = it.value, algorithm = it.algorithm) },
    metadata = FileRecord.Metadata(
        size = metadata.size,
        lastModified = metadata.lastModified,
        version = metadata.version?.toFiles(),
    ),
)

/**
 * What a peer reported, as the remote index records it.
 *
 * @param seenAt when we heard it, not when it happened.
 */
internal fun FileRecordDto.toRemoteIndexed(seenAt: Instant) = RemoteIndexedFile(
    sourceId = sourceId,
    fileId = id,
    path = path,
    state = state.toIndexed(),
    size = FileSize(metadata.size),
    modifiedAt = metadata.lastModified,
    hash = content?.let { ContentHash(value = it.value, algorithm = it.algorithm) },
    version = metadata.version?.toIndexed(),
    seenAt = seenAt,
)

/** Zero counter means "no edits", same as an absent one, so it is dropped rather than refused. */
internal fun VersionDto.toFiles() =
    FileVersion(
        vector = VersionVector(vector.filterValues { it != 0L }),
        hlc = hlc,
        originDevice = originDevice
    )

internal fun VersionDto.toIndexed(): LocalIndexedFile.Version = toFiles().toIndexed()

internal fun FileVersion.toDto() =
    VersionDto(vector = vector.counters, hlc = hlc, originDevice = originDevice)

private fun LocalIndexedFile.Version.toDto() =
    VersionDto(vector = vector.counters, hlc = hlc.packed, originDevice = originDevice)

private fun FileRecordDto.State.toIndexed(): LocalIndexedFile.State = when (this) {
    is FileRecordDto.State.Present -> LocalIndexedFile.State.Present(pinned)

    is FileRecordDto.State.Evicted -> LocalIndexedFile.State.Evicted(evictedAt)

    is FileRecordDto.State.Deleted -> LocalIndexedFile.State.Deleted(deletedAt)
}

private fun LocalIndexedFile.State.toDto(): FileRecordDto.State = when (this) {
    is LocalIndexedFile.State.Present -> FileRecordDto.State.Present(pinned)

    is LocalIndexedFile.State.Evicted -> FileRecordDto.State.Evicted(evictedAt)

    is LocalIndexedFile.State.Deleted -> FileRecordDto.State.Deleted(deletedAt)
}

private fun FileRecord.State.toDto(): FileRecordDto.State = when (this) {
    is FileRecord.State.Present -> FileRecordDto.State.Present(pinned)

    is FileRecord.State.Evicted -> FileRecordDto.State.Evicted(evictedAt)

    is FileRecord.State.Deleted -> FileRecordDto.State.Deleted(deletedAt)
}

private fun FileRecordDto.State.toDomain(): FileRecord.State = when (this) {
    is FileRecordDto.State.Present -> FileRecord.State.Present(pinned)

    is FileRecordDto.State.Evicted -> FileRecord.State.Evicted(evictedAt)

    is FileRecordDto.State.Deleted -> FileRecord.State.Deleted(deletedAt)
}

/** [deviceId] is who sent it, never a field of the message: a peer only reports its own half. */
internal fun SourceMetadataDto.toDomain(
    sourceId: String,
    deviceId: String,
    updatedAt: Instant,
): PeerSourceMetadata = PeerSourceMetadata(
    sourceId = sourceId,
    deviceId = deviceId,
    storageKind = storageKind.toDomain(),
    storagePath = storagePath,
    usage = SourceUsage(files = files, bytes = bytes),
    usedPercent = usedPercent,
    updatedAt = updatedAt,
)

internal fun PeerSourceMetadata.StorageKind.toDto(): SourceMetadataDto.StorageKind = when (this) {
    PeerSourceMetadata.StorageKind.Folder -> SourceMetadataDto.StorageKind.Folder
    PeerSourceMetadata.StorageKind.AppStorage -> SourceMetadataDto.StorageKind.AppStorage
    PeerSourceMetadata.StorageKind.Media -> SourceMetadataDto.StorageKind.Media
}

internal fun SourceMetadataDto.StorageKind.toDomain(): PeerSourceMetadata.StorageKind = when (this) {
    SourceMetadataDto.StorageKind.Folder -> PeerSourceMetadata.StorageKind.Folder
    SourceMetadataDto.StorageKind.AppStorage -> PeerSourceMetadata.StorageKind.AppStorage
    SourceMetadataDto.StorageKind.Media -> PeerSourceMetadata.StorageKind.Media
}
