package com.fserver.core.support

import com.fserver.common.model.FileSize
import com.fserver.core.files.SourceLocation
import com.fserver.core.network.dictionary.dto.ContentHashDto
import com.fserver.core.network.dictionary.dto.FileRecordDto
import com.fserver.core.network.dictionary.dto.SourceMetadataDto
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.metadata.PeerMetadataExchange
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import com.fserver.core.util.TimeProvider
import com.fserver.net.security.identity.PeerIdentity
import kotlin.time.Instant

internal val TestEpoch: Instant = Instant.fromEpochSeconds(1_000_000)

/** A peer whose key is derived from its id, so two fixtures never collide by accident. */
internal fun peerIdentity(deviceId: String): PeerIdentity =
    PeerIdentity(deviceId = deviceId, publicKey = "key-of-$deviceId".toByteArray())

internal fun sourceEntry(
    id: String = "source-1",
    deviceId: String = "device-peer",
    location: SourceLocation.Persistable = SourceLocation.Internal(bucket = id),
    syncMode: SyncMode = SyncMode.Mirror(SyncMode.Mirror.ConflictResolution.LastWriteWins),
    role: SourceEntry.Role = SourceEntry.Role.Follower,
    status: SourceEntry.Status = SourceEntry.Status.Active,
    label: String = "Test source",
    createdAt: Instant = TestEpoch,
): SourceEntry = SourceEntry(
    id = id,
    deviceId = deviceId,
    location = location,
    syncMode = syncMode,
    role = role,
    status = status,
    label = label,
    createdAt = createdAt,
)

/** Reports sources off [storage] with no mounted volumes: paths come out volume-relative as-is. */
internal fun peerMetadataExchange(storage: FServerStorage, clock: TimeProvider): PeerMetadataExchange =
    PeerMetadataExchange(storage, clock) { emptyList() }

internal fun sourceMetadataDto(
    storagePath: String = "/DCIM/Camera",
    files: Int = 3,
    bytes: Long = 300,
    usedPercent: Float? = null,
): SourceMetadataDto = SourceMetadataDto(storagePath, files, bytes, usedPercent)

internal fun fileDto(
    id: String = "file-1",
    sourceId: String = "source-1",
    path: String = "photo.jpg",
    size: Long = 0,
    lastModified: Instant = TestEpoch,
    state: FileRecordDto.State = FileRecordDto.State.Present(),
    content: ContentHashDto? = null,
): FileRecordDto = FileRecordDto(
    id = id,
    sourceId = sourceId,
    path = path,
    state = state,
    content = content,
    metadata = FileRecordDto.Metadata(
        size = size,
        lastModified = lastModified,
        version = null,
    ),
)

internal fun indexedFile(
    id: String = "row-1",
    sourceId: String = "source-1",
    fileId: String = "file-1",
    path: String = "photo.jpg",
    locator: String = "/tmp/photo.jpg",
    state: LocalIndexedFile.State = LocalIndexedFile.State.Present(),
    size: Long = 0,
    modifiedAt: Instant = TestEpoch,
): LocalIndexedFile = LocalIndexedFile(
    id = id,
    sourceId = sourceId,
    fileId = fileId,
    path = path,
    locator = locator,
    state = state,
    size = FileSize(size),
    modifiedAt = modifiedAt,
    processedAt = modifiedAt,
)
