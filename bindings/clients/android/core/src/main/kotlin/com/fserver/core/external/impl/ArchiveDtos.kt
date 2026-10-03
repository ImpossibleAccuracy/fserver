package com.fserver.core.external.impl

import com.fserver.common.model.ContentHash
import com.fserver.core.crypto.model.EncryptionPolicy
import com.fserver.core.files.SourceLocation
import com.fserver.core.network.auth.OfferedAuthMethod
import com.fserver.core.network.device.model.LocalDevice
import com.fserver.core.network.device.model.TrustedDevice
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.index.RemoteIndexedFile
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * The archive's own format, versioned by [ManifestDto.formatVersion]. Mirrors the domain on purpose:
 * renaming a domain field must not silently change what an older archive means on import.
 */

@Serializable
internal data class ManifestDto(
    val format: String = Format,
    val formatVersion: Int = FormatVersion,
    val exportedAt: String,
    val scope: Scope,
    val device: DeviceDto,
    val sources: List<SourceRefDto>,
    val files: Int,
    val bytes: Long,
    val metadataOnly: Int,
    val skipped: Int,
) {
    @Serializable
    data class SourceRefDto(val id: String, val label: String)

    /** Whether the archive holds every source, or only those listed in [sources]. */
    @Serializable
    enum class Scope {
        @SerialName("all") All,
        @SerialName("sources") Sources,
    }

    companion object {
        const val Format = "fserver-export"
        const val FormatVersion = 1
    }
}

@Serializable
internal data class DeviceDto(val id: String, val name: String, val kind: String?)

/** Which methods are offered, never their secrets. */
@Serializable
internal data class AuthDto(val offeredMethods: List<String>)

@Serializable
internal data class TrustedDeviceDto(
    val deviceId: String,
    val displayName: String,
    val publicKeyHex: String,
    val fingerprint: String,
    val method: String,
    val strength: String,
)

@Serializable
internal data class SourceDto(
    val id: String,
    val label: String,
    val peerDeviceId: String,
    val role: String,
    val status: String,
    val disabledReason: String? = null,
    val createdAt: String,
    val lastSyncedAt: String? = null,
    val location: LocationDto,
    val syncMode: SyncModeDto,
    val preferences: PreferencesDto,
)

@Serializable
internal data class LocationDto(val type: String, val path: String? = null)

@Serializable
internal data class SyncModeDto(
    val type: String,
    val conflictResolution: String? = null,
    val ignoreFilesBefore: String? = null,
    val evictOlderThanDays: Int? = null,
    val evictLargerThanBytes: Long? = null,
)

@Serializable
internal data class PreferencesDto(
    val wifiRequired: Boolean,
    val chargingRequired: Boolean,
    val maxFiles: Int? = null,
    val maxTotalBytes: Long? = null,
    /** Cipher the source is sealed with on this device; the archive itself is always plaintext. */
    val encryptionCipher: String? = null,
)

@Serializable
internal data class FileDto(
    val fileId: String,
    val path: String,
    val held: Held,
    /** Whether the bytes are in the archive, under `files/<source id>/<path>`. */
    val archived: Boolean,
    val size: Long,
    val modifiedAt: String,
    val hash: HashDto? = null,
    val version: VersionDto? = null,
    val pinned: Boolean = false,
    val evictedAt: String? = null,
) {
    @Serializable
    enum class Held {
        @SerialName("here") Here,
        @SerialName("evicted") Evicted,
        @SerialName("peer") Peer,
    }
}

@Serializable
internal data class HashDto(val algorithm: String, val value: String)

@Serializable
internal data class VersionDto(
    val vector: Map<String, Long>,
    val hlc: Long,
    val originDevice: String,
)

internal fun LocalDevice.toDto() = DeviceDto(id = deviceId, name = displayName, kind = kind?.name)

internal fun List<OfferedAuthMethod>.toDto() = AuthDto(offeredMethods = map { it.method.name })

@OptIn(ExperimentalStdlibApi::class)
internal fun TrustedDevice.toDto() = TrustedDeviceDto(
    deviceId = deviceId,
    displayName = displayName,
    publicKeyHex = publicKey.toHexString(),
    fingerprint = fingerprint.value,
    method = method.name,
    strength = strength,
)

internal fun SourceEntry.toDto() = SourceDto(
    id = id,
    label = label,
    peerDeviceId = deviceId,
    role = role.name,
    status = when (status) {
        SourceEntry.Status.Active -> "Active"
        SourceEntry.Status.Pending -> "Pending"
        is SourceEntry.Status.Disabled -> "Disabled"
    },
    disabledReason = (status as? SourceEntry.Status.Disabled)?.reason,
    createdAt = createdAt.toString(),
    lastSyncedAt = lastSyncedAt?.toString(),
    location = location.toDto(),
    syncMode = syncMode.toDto(),
    preferences = PreferencesDto(
        wifiRequired = preferences.deviceConstraints.wifiRequired,
        chargingRequired = preferences.deviceConstraints.chargingRequired,
        maxFiles = preferences.fileLimits.maxFiles,
        maxTotalBytes = preferences.fileLimits.maxTotalSize?.bytes,
        encryptionCipher = (preferences.encryption as? EncryptionPolicy.Required)?.cipherId,
    ),
)

private fun SourceLocation.Persistable.toDto(): LocationDto = when (this) {
    is SourceLocation.Tree -> LocationDto("Tree", path)
    is SourceLocation.Directory -> LocationDto("Directory", path)
    is SourceLocation.Downloads -> LocationDto("Downloads", directory)
    is SourceLocation.Internal -> LocationDto("Internal", bucket)
    SourceLocation.Media -> LocationDto("Media")
}

private fun SyncMode.toDto(): SyncModeDto = when (this) {
    is SyncMode.Mirror -> SyncModeDto(type.name, conflictResolution = conflictResolution.name)
    is SyncMode.AutoUpload -> SyncModeDto(type.name, ignoreFilesBefore = ignoreFilesBefore?.toString())
    is SyncMode.Offload -> SyncModeDto(
        type.name,
        evictOlderThanDays = (policy as? SyncMode.Offload.EvictPolicy.OlderThanDays)?.days,
        evictLargerThanBytes = (policy as? SyncMode.Offload.EvictPolicy.LargerThanBytes)?.bytes,
    )
    SyncMode.Host -> SyncModeDto(type.name)
}

internal fun LocalIndexedFile.toDto(archived: Boolean) = FileDto(
    fileId = fileId,
    path = path,
    held = if (state is LocalIndexedFile.State.Evicted) FileDto.Held.Evicted else FileDto.Held.Here,
    archived = archived,
    size = size.bytes,
    modifiedAt = modifiedAt.toString(),
    hash = hash?.toDto(),
    version = version?.toDto(),
    pinned = (state as? LocalIndexedFile.State.Present)?.pinned == true,
    evictedAt = (state as? LocalIndexedFile.State.Evicted)?.evictedAt?.toString(),
)

internal fun RemoteIndexedFile.toDto() = FileDto(
    fileId = fileId,
    path = path,
    held = FileDto.Held.Peer,
    archived = false,
    size = size.bytes,
    modifiedAt = modifiedAt.toString(),
    hash = hash?.toDto(),
    version = version?.toDto(),
)

private fun ContentHash.toDto() = HashDto(algorithm = algorithm, value = value)

private fun LocalIndexedFile.Version.toDto() = VersionDto(
    vector = vector.counters,
    hlc = hlc.packed,
    originDevice = originDevice,
)
