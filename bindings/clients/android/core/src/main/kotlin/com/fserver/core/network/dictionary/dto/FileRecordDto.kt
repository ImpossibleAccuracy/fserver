package com.fserver.core.network.dictionary.dto

import com.fserver.common.model.ContentHash
import com.fserver.common.model.FileSize
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.index.RemoteIndexedFile
import com.fserver.files.upload.FileId
import com.fserver.files.upload.FileRecord
import com.fserver.files.upload.Revision
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Wire form of [FileRecord].
 *
 * Mirrored rather than annotating [FileRecord] itself: the domain model is free to change shape,
 * the wire format is versioned and negotiated with peers that may be older or on another platform.
 */
@Serializable
internal data class FileRecordDto(
    val id: String,
    val sourceId: String,
    val path: String,
    val state: State,
    /** Content identity, or null while the peer's index has not hashed the file yet. */
    val content: ContentHashDto?,
    val metadata: Metadata,
) {
    @Serializable
    data class Metadata(
        val size: Long,
        val lastModified: Instant,
        /** Who last wrote the file and how many times, or null when the peer does not report it. */
        val revision: RevisionDto?,
    )

    /**
     * What the sending side holds right now.
     *
     * The [Evicted] / [Deleted] split is load-bearing: eviction frees local space and must never
     * reach the other side as a user deletion. Collapsing them loses user data.
     */
    @Serializable
    sealed interface State {
        @Serializable
        data class Present(val pinned: Boolean = false) : State

        @Serializable
        data class Evicted(val evictedAt: Instant) : State

        @Serializable
        data class Deleted(val deletedAt: Instant) : State
    }
}

@Serializable
internal data class ContentHashDto(
    val value: String,
    val algorithm: String,
)

@Serializable
internal data class RevisionDto(
    val originDevice: String,
    val counter: Long,
)

internal fun LocalIndexedFile.toDto(): FileRecordDto = FileRecordDto(
    id = fileId,
    sourceId = sourceId,
    path = path,
    state = state.toDto(),
    content = hash?.let { ContentHashDto(value = it.value, algorithm = it.algorithm) },
    metadata = FileRecordDto.Metadata(
        size = size.bytes,
        lastModified = modifiedAt,
        revision = revision?.let {
            RevisionDto(originDevice = it.originDevice, counter = it.counter)
        },
    ),
)

internal fun FileRecord.toDto(sourceId: String): FileRecordDto = FileRecordDto(
    id = id.value,
    sourceId = sourceId,
    path = path,
    state = state.toDto(),
    content = content?.let { ContentHashDto(value = it.value, algorithm = it.algorithm) },
    metadata = FileRecordDto.Metadata(
        size = metadata.size,
        lastModified = metadata.lastModified,
        revision = metadata.revision?.let {
            RevisionDto(originDevice = it.originDevice, counter = it.counter)
        },
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
        revision = metadata.revision?.let {
            Revision(originDevice = it.originDevice, counter = it.counter)
        },
    ),
)

/** What a peer reported, as the remote index records it. [seenAt] is when we heard it, not when it happened. */
internal fun FileRecordDto.toRemoteIndexed(seenAt: Instant): RemoteIndexedFile = RemoteIndexedFile(
    sourceId = sourceId,
    fileId = id,
    path = path,
    state = state.toIndexed(),
    size = FileSize(metadata.size),
    modifiedAt = metadata.lastModified,
    hash = content?.let { ContentHash(value = it.value, algorithm = it.algorithm) },
    revision = metadata.revision?.let {
        LocalIndexedFile.Revision(originDevice = it.originDevice, counter = it.counter)
    },
    seenAt = seenAt,
)

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
