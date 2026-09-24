package com.fserver.app.presentation.screens.source.shared.model

import com.fserver.core.network.device.model.TrustedDevice
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode

/** The mode as the setup flow names it — one vocabulary for both halves of a source. */
fun SyncMode.toUi(): SourceModeUi = type.toUi()

fun SyncMode.Type.toUi(): SourceModeUi = when (this) {
    SyncMode.Type.Mirror -> SourceModeUi.Sync
    SyncMode.Type.AutoUpload -> SourceModeUi.AutoUpload
    SyncMode.Type.Offload -> SourceModeUi.Offload
}

fun SourceEntry.Role.toUi(): SourceRoleUi = when (this) {
    SourceEntry.Role.Initiator -> SourceRoleUi.Initiator
    SourceEntry.Role.Follower -> SourceRoleUi.Follower
}

fun List<TrustedDevice>.latest(deviceId: String): TrustedDevice? =
    filter { it.deviceId == deviceId }.latest()

fun List<TrustedDevice>.latest(): TrustedDevice? = this
    .filter { it.metadata?.lastSeen != null }
    .maxByOrNull { it.metadata!!.lastSeen!! }

fun List<TrustedDevice>.nameOf(deviceId: String): String =
    latest(deviceId)?.displayName ?: deviceId
