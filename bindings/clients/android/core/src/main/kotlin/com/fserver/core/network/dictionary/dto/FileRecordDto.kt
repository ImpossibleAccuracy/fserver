package com.fserver.core.network.dictionary.dto

import com.fserver.common.model.ContentHash
import com.fserver.common.model.FileSize
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.index.RemoteIndexedFile
import com.fserver.core.sync.index.toFiles
import com.fserver.core.sync.index.toIndexed
import com.fserver.core.sync.version.HlcTimestamp
import com.fserver.files.upload.FileId
import com.fserver.files.upload.FileRecord
import com.fserver.files.upload.FileVersion
import com.fserver.files.upload.VersionVector
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
        /** Where this version sits in the file's history, or null when the peer does not report it. */
        val version: VersionDto?,
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
internal data class VersionDto(
    /** Edit count per device id. */
    val vector: Map<String, Long>,
    /** Packed HLC reading: 48 bits of millis, 16 bits of counter. */
    val hlc: Long,
    val originDevice: String,
)

/**
 * Newest HLC reading among [this], for the local clock to catch up with. A malformed one is skipped:
 * it fails only the file carrying it, not the whole index.
 */
internal fun List<FileRecordDto>.latestHlc(): HlcTimestamp? =
    mapNotNull { dto -> dto.metadata.version?.hlc?.takeIf { it >= 0 } }
        .maxOrNull()
        ?.let(::HlcTimestamp)
