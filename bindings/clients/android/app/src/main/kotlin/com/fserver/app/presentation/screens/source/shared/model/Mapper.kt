package com.fserver.app.presentation.screens.source.shared.model

import com.fserver.core.network.device.model.TrustedDevice
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode

/** The mode as the setup flow names it — one vocabulary for both halves of a source. */
fun SyncMode.toUi(): SourceModeUi = when (this) {
    SyncMode.Mirror -> SourceModeUi.Sync
    is SyncMode.AutoUpload -> SourceModeUi.AutoUpload
    is SyncMode.Offload -> SourceModeUi.Offload
}

fun SourceEntry.Role.toUi(): SourceRoleUi = when (this) {
    SourceEntry.Role.Initiator -> SourceRoleUi.Initiator
    SourceEntry.Role.Follower -> SourceRoleUi.Follower
}

/**
 * The peer's name as the trust records know it, falling back to its id.
 *
 * Trust rather than the online list: a source outlives any one session, so the device it is
 * paired with is usually not on the network while a screen naming it is open.
 */
fun List<TrustedDevice>.nameOf(deviceId: String): String = this
    .filter { it.deviceId == deviceId }
    .maxByOrNull { it.lastSeen }
    ?.displayName
    ?: deviceId
