package com.fserver.core.network.dictionary.dto

import com.fserver.core.sync.model.SyncMode
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Wire form of [SyncMode].
 *
 * Mirrored rather than annotating [SyncMode] itself, for the same reason as [FileRecordDto]: the
 * domain model is free to change shape, this is negotiated with peers that may be older or on
 * another platform.
 */
@Serializable
internal sealed interface SyncModeDto {
    @Serializable
    data class Mirror(
        val conflictResolution: ConflictResolution = ConflictResolution.LastWriteWins,
    ) : SyncModeDto {
        @Serializable
        enum class ConflictResolution { Ask, LastWriteWins }
    }

    @Serializable
    data class AutoUpload(val ignoreFilesBefore: Instant?) : SyncModeDto

    @Serializable
    data class Offload(
        val policy: EvictPolicy,
    ) : SyncModeDto {
        @Serializable
        sealed interface EvictPolicy {
            @Serializable
            data class OlderThanDays(val days: Int) : EvictPolicy

            @Serializable
            data class LargerThanBytes(val bytes: Long) : EvictPolicy
        }
    }

    @Serializable
    data object Host : SyncModeDto
}

internal fun SyncMode.toDto(): SyncModeDto = when (this) {
    is SyncMode.Mirror -> SyncModeDto.Mirror(
        conflictResolution = when (conflictResolution) {
            SyncMode.Mirror.ConflictResolution.LastWriteWins ->
                SyncModeDto.Mirror.ConflictResolution.LastWriteWins

            SyncMode.Mirror.ConflictResolution.Ask ->
                SyncModeDto.Mirror.ConflictResolution.Ask
        },
    )

    is SyncMode.AutoUpload -> SyncModeDto.AutoUpload(ignoreFilesBefore)

    is SyncMode.Offload -> SyncModeDto.Offload(policy = policy.toDto())

    SyncMode.Host -> SyncModeDto.Host
}

internal fun SyncModeDto.toDomain(): SyncMode = when (this) {
    is SyncModeDto.Mirror -> SyncMode.Mirror(
        conflictResolution = when (conflictResolution) {
            SyncModeDto.Mirror.ConflictResolution.LastWriteWins ->
                SyncMode.Mirror.ConflictResolution.LastWriteWins

            SyncModeDto.Mirror.ConflictResolution.Ask ->
                SyncMode.Mirror.ConflictResolution.Ask
        },
    )

    is SyncModeDto.AutoUpload -> SyncMode.AutoUpload(ignoreFilesBefore)

    is SyncModeDto.Offload -> SyncMode.Offload(policy = policy.toDomain())

    SyncModeDto.Host -> SyncMode.Host
}

private fun SyncMode.Offload.EvictPolicy.toDto(): SyncModeDto.Offload.EvictPolicy = when (this) {
    is SyncMode.Offload.EvictPolicy.OlderThanDays ->
        SyncModeDto.Offload.EvictPolicy.OlderThanDays(days)

    is SyncMode.Offload.EvictPolicy.LargerThanBytes ->
        SyncModeDto.Offload.EvictPolicy.LargerThanBytes(bytes)
}

private fun SyncModeDto.Offload.EvictPolicy.toDomain(): SyncMode.Offload.EvictPolicy = when (this) {
    is SyncModeDto.Offload.EvictPolicy.OlderThanDays ->
        SyncMode.Offload.EvictPolicy.OlderThanDays(days)

    is SyncModeDto.Offload.EvictPolicy.LargerThanBytes ->
        SyncMode.Offload.EvictPolicy.LargerThanBytes(bytes)
}
