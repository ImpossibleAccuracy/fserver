package com.fserver.files.upload.impl

import com.fserver.common.model.ContentHash
import com.fserver.files.upload.FileAction
import com.fserver.files.upload.FileId
import com.fserver.files.upload.FileRecord
import com.fserver.files.upload.FileVersion
import com.fserver.files.upload.FilesSnapshot
import com.fserver.files.upload.UploadStrategy
import com.fserver.files.upload.VersionVector
import kotlin.time.Instant

internal const val A = "device-a"
internal const val B = "device-b"
internal val Early: Instant = Instant.fromEpochSeconds(1_000_000)
internal val Late: Instant = Instant.fromEpochSeconds(2_000_000)
internal val Now: Instant = Instant.fromEpochSeconds(3_000_000)
internal val Deleted = FileRecord.State.Deleted(Early)
internal val Evicted = FileRecord.State.Evicted(Early)
internal val Pinned = FileRecord.State.Present(pinned = true)
internal val Fetched = FileRecord.State.Present(fetchedAt = Early)

/** The single action planned for one file seen as [local] / [remote]; null sides are absent. */
internal suspend fun UploadStrategy.planOne(
    params: UploadStrategy.Params,
    local: FileRecord?,
    remote: FileRecord?,
): FileAction? = plan(params, FilesSnapshot(listOfNotNull(local), listOfNotNull(remote), Now)).actions.singleOrNull()

internal fun record(
    vector: Map<String, Long>?,
    content: String? = null,
    state: FileRecord.State = FileRecord.State.Present(),
    modifiedAt: Instant = Early,
    accessedAt: Instant? = null,
    size: Long = 64,
    origin: String = A,
    hlc: Long = 0,
) = FileRecord(
    id = FileId("file-1"),
    path = "photo.jpg",
    locator = "photo.jpg",
    state = state,
    content = content?.let { ContentHash(value = it, algorithm = "SHA-256") },
    metadata = FileRecord.Metadata(
        size = size,
        lastModified = modifiedAt,
        version = vector?.let { FileVersion(VersionVector(it), hlc = hlc, originDevice = origin) },
        lastAccessed = accessedAt,
    ),
)
