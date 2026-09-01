package com.fserver.core.sync.index

import com.fserver.files.upload.FileId
import com.fserver.files.upload.FileRecord
import com.fserver.files.upload.Revision

/** This device's side of a file, as a strategy wants to see it. */
internal fun IndexedFile.toFileRecord(): FileRecord = FileRecord(
    id = FileId(fileId),
    path = path,
    state = state.toFiles(),
    content = hash,
    metadata = FileRecord.Metadata(
        size = size.bytes,
        lastModified = modifiedAt,
        revision = revision?.let { Revision(originDevice = it.originDevice, counter = it.counter) },
    ),
)

private fun IndexedFile.State.toFiles(): FileRecord.State = when (this) {
    is IndexedFile.State.Present -> FileRecord.State.Present(
        location = location,
        pinned = pinned,
    )

    is IndexedFile.State.Evicted -> FileRecord.State.Evicted(evictedAt)

    is IndexedFile.State.Deleted -> FileRecord.State.Deleted(deletedAt)
}
