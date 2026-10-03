package com.fserver.core.sync.index

import com.fserver.common.model.FileSize
import com.fserver.files.upload.FileId
import com.fserver.files.upload.FileRecord
import com.fserver.core.sync.version.HlcTimestamp
import com.fserver.core.sync.version.VersionVector
import com.fserver.files.upload.FileVersion
import com.fserver.files.upload.VersionVector as FilesVersionVector
import kotlin.time.Instant

/** This device's side of a file, as a strategy wants to see it. */
internal fun LocalIndexedFile.toFileRecord(): FileRecord = FileRecord(
    id = FileId(fileId),
    path = path,
    locator = locator,
    state = state.toFiles(),
    content = hash.takeUnless { hashStale },
    metadata = FileRecord.Metadata(
        size = size.bytes,
        lastModified = modifiedAt,
        version = version?.toFiles(),
        // Android gives no reliable atime (noatime mounts, MediaStore/SAF expose none).
        lastAccessed = null,
    ),
)

/** The peer's side of a file, from what it last reported. It has no locator here. */
internal fun RemoteIndexedFile.toFileRecord(): FileRecord = FileRecord(
    id = FileId(fileId),
    path = path,
    locator = null,
    state = state.toFiles(),
    content = hash,
    metadata = FileRecord.Metadata(
        size = size.bytes,
        lastModified = modifiedAt,
        version = version?.toFiles(),
        lastAccessed = null,
    ),
)

internal fun FileRecord.toIndexed(
    id: String,
    sourceId: String,
    locator: String,
    currentTime: Instant,
): LocalIndexedFile = LocalIndexedFile(
    id = id,
    sourceId = sourceId,
    fileId = this.id.value,
    path = path,
    locator = locator,
    state = state.toIndexed(),
    hash = content,
    size = FileSize(metadata.size),
    modifiedAt = metadata.lastModified,
    version = metadata.version?.toIndexed(),
    processedAt = currentTime,
)

private fun LocalIndexedFile.State.toFiles(): FileRecord.State = when (this) {
    is LocalIndexedFile.State.Present -> FileRecord.State.Present(pinned, fetchedAt)

    is LocalIndexedFile.State.Evicted -> FileRecord.State.Evicted(evictedAt)

    is LocalIndexedFile.State.Deleted -> FileRecord.State.Deleted(deletedAt)
}

private fun FileRecord.State.toIndexed(): LocalIndexedFile.State = when (this) {
    is FileRecord.State.Present -> LocalIndexedFile.State.Present(pinned, fetchedAt)

    is FileRecord.State.Evicted -> LocalIndexedFile.State.Evicted(evictedAt)

    is FileRecord.State.Deleted -> LocalIndexedFile.State.Deleted(deletedAt)
}

internal fun LocalIndexedFile.Version.toFiles(): FileVersion = FileVersion(
    vector = FilesVersionVector(vector.counters),
    hlc = hlc.packed,
    originDevice = originDevice,
)

internal fun FileVersion.toIndexed(): LocalIndexedFile.Version = LocalIndexedFile.Version(
    vector = VersionVector(vector.counters),
    hlc = HlcTimestamp(hlc),
    originDevice = originDevice,
)
