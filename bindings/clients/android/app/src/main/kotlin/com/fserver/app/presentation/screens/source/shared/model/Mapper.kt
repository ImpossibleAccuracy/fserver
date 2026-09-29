package com.fserver.app.presentation.screens.source.shared.model

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

/** Where this device keeps the source: the picked folder, or the one it was given. */
fun SourceEntry.localPath(): String? =
    if (role == SourceEntry.Role.Initiator) originPath else location.readablePath()

/** What this pass has moved for [sourceId] so far. */
fun SourcePass?.transfersOf(sourceId: String, transfers: List<FileTransfer>): List<FileTransfer> =
    if (this == null) emptyList()
    else transfers.filter { it.key.sourceId == sourceId && it.startedAt >= startedAt }
