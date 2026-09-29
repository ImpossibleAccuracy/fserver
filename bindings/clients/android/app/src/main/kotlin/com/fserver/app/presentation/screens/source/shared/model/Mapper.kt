package com.fserver.app.presentation.screens.source.shared.model

import com.fserver.app.R
import com.fserver.app.presentation.model.UiText
import com.fserver.core.sync.metadata.PeerSourceMetadata
import com.fserver.core.sync.progress.FileTransfer
import com.fserver.core.sync.progress.SourcePass
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode

/** The mode as the setup flow names it — one vocabulary for both halves of a source. */
fun SyncMode.toUi(): SourceModeUi = type.toUi()

fun SyncMode.Type.toUi(): SourceModeUi = when (this) {
    SyncMode.Type.Mirror -> SourceModeUi.Sync
    SyncMode.Type.AutoUpload -> SourceModeUi.AutoUpload
    SyncMode.Type.Offload -> SourceModeUi.Offload
    SyncMode.Type.Host -> SourceModeUi.Host
}

fun SourceEntry.Role.toUi(): SourceRoleUi = when (this) {
    SourceEntry.Role.Initiator -> SourceRoleUi.Initiator
    SourceEntry.Role.Follower -> SourceRoleUi.Follower
}

/** This device's half of [source]: of its two devices, the one that is not the peer. */
fun List<PeerSourceMetadata>.ownHalfOf(source: SourceEntry): PeerSourceMetadata? =
    firstOrNull { it.sourceId == source.id && it.deviceId != source.deviceId }

fun List<PeerSourceMetadata>.peerHalfOf(source: SourceEntry): PeerSourceMetadata? =
    firstOrNull { it.sourceId == source.id && it.deviceId == source.deviceId }

/** The half the files come from. Null on a one-way follower, which the initiator never reports to. */
fun List<PeerSourceMetadata>.initiatorHalfOf(source: SourceEntry): PeerSourceMetadata? =
    if (source.role == SourceEntry.Role.Initiator) ownHalfOf(source) else peerHalfOf(source)

/** Where the device keeps the files, as a person reads it. */
fun PeerSourceMetadata.storageLabel(): UiText? = when (storageKind) {
    PeerSourceMetadata.StorageKind.Folder -> storagePath?.let(UiText::Text)
    PeerSourceMetadata.StorageKind.AppStorage -> UiText.of(R.string.sync_request_location_internal_title)
    PeerSourceMetadata.StorageKind.Media -> UiText.of(R.string.source_summary_location_media)
}

/** What this pass has moved for [sourceId] so far. */
fun SourcePass?.transfersOf(sourceId: String, transfers: List<FileTransfer>): List<FileTransfer> =
    if (this == null) emptyList()
    else transfers.filter { it.key.sourceId == sourceId && it.startedAt >= startedAt }
