package com.fserver.core.sync.index

import com.fserver.common.model.FileSize
import com.fserver.files.upload.FileId
import com.fserver.files.upload.FileRecord
import com.fserver.files.upload.Revision
import kotlin.time.Instant

/** This device's side of a file, as a strategy wants to see it. */
internal fun IndexedFile.toFileRecord(): FileRecord = FileRecord(
    id = FileId(fileId),
    path = path,
    locator = locator,
    state = state.toFiles(),
    content = hash,
    metadata = FileRecord.Metadata(
        size = size.bytes,
        lastModified = modifiedAt,
        revision = revision?.let { Revision(originDevice = it.originDevice, counter = it.counter) },
    ),
)

internal fun FileRecord.toIndexed(
    id: String,
    sourceId: String,
    locator: String,
    currentTime: Instant,
): IndexedFile = IndexedFile(
    id = id,
    sourceId = sourceId,
    fileId = this.id.value,
    path = path,
    locator = locator,
    state = state.toIndexed(),
    hash = content,
    size = FileSize(metadata.size),
    modifiedAt = metadata.lastModified,
    revision = metadata.revision?.let {
        IndexedFile.Revision(
            originDevice = it.originDevice,
            counter = it.counter
        )
    },
    processedAt = currentTime,
)

private fun IndexedFile.State.toFiles(): FileRecord.State = when (this) {
    is IndexedFile.State.Present -> FileRecord.State.Present(pinned)

    is IndexedFile.State.Evicted -> FileRecord.State.Evicted(evictedAt)

    is IndexedFile.State.Deleted -> FileRecord.State.Deleted(deletedAt)
}

private fun FileRecord.State.toIndexed(): IndexedFile.State = when (this) {
    is FileRecord.State.Present -> IndexedFile.State.Present(pinned)

    is FileRecord.State.Evicted -> IndexedFile.State.Evicted(evictedAt)

    is FileRecord.State.Deleted -> IndexedFile.State.Deleted(deletedAt)
}
