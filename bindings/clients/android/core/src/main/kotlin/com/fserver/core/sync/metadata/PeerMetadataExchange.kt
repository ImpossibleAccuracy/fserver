package com.fserver.core.sync.metadata

import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.files.SourceLocation
import com.fserver.core.files.toOriginPath
import com.fserver.core.network.dictionary.dto.SourceMetadataDto
import com.fserver.core.network.dictionary.dto.toDomain
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.limits.usedPercent
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.receivesMetadata
import com.fserver.core.sync.model.sharesMetadata
import com.fserver.core.util.TimeProvider
import timber.log.Timber

/**
 * Builds what this device tells the peer about its half of a source, and records both halves.
 * Best effort: metadata is informational and must never fail a pass.
 */
internal class PeerMetadataExchange(
    private val storage: FServerStorage,
    private val timeProvider: TimeProvider,
    private val volumes: () -> List<SourceLocation.Root.Volume>,
) {
    /** This device's half of [source], recorded here too. Throws if the index cannot be read. */
    suspend fun describe(source: SourceEntry): SourceMetadataDto {
        val usage = storage.index.presentUsage(source.id)

        val dto = SourceMetadataDto(
            storagePath = source.location.toOriginPath(volumes()),
            files = usage.files,
            bytes = usage.bytes,
            usedPercent = source.preferences.fileLimits.usedPercent(usage),
        )

        record(
            sourceId = source.id,
            deviceId = storage.identity.localDevice().deviceId,
            metadata = dto
        )

        return dto
    }

    /** [describe], or null when it fails. */
    suspend fun refresh(source: SourceEntry): SourceMetadataDto? =
        runCatchingCancellable { describe(source) }
            .onFailure { Timber.w(it, "Could not describe source ${source.id}") }
            .getOrNull()

    /** What goes with a lease message: null when this end does not report [source] to its peer. */
    suspend fun forLease(source: SourceEntry): SourceMetadataDto? =
        refresh(source)?.takeIf { source.sharesMetadata }

    /**
     * The peer's half of [source], sent with a lease message. Dropped where the rules say the peer
     * does not report it: a peer that sends anyway is not followed.
     */
    suspend fun recordFromPeer(source: SourceEntry, metadata: SourceMetadataDto?) {
        if (metadata == null) return

        if (!source.receivesMetadata) {
            Timber.w("Source ${source.id}: ${source.deviceId} reported metadata it does not share under ${source.syncMode.type}")
            return
        }

        record(source.id, source.deviceId, metadata)
    }

    /** [deviceId]'s half of [sourceId], as it reported it. */
    private suspend fun record(sourceId: String, deviceId: String, metadata: SourceMetadataDto?) {
        if (metadata == null) return

        record(metadata.toDomain(sourceId = sourceId, deviceId = deviceId, updatedAt = timeProvider.now()))
    }

    suspend fun record(metadata: PeerSourceMetadata) {
        runCatchingCancellable { storage.sources.recordMetadata(metadata) }
            .onFailure { Timber.w(it, "Could not record metadata of ${metadata.deviceId} for source ${metadata.sourceId}") }
    }
}
