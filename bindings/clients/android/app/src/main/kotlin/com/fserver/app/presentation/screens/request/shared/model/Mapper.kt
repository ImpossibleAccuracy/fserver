package com.fserver.app.presentation.screens.request.shared.model

import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.core.files.SourceLocation
import com.fserver.core.network.device.model.TrustedDevice
import com.fserver.core.sync.SyncMode
import com.fserver.core.sync.setup.IncomingSourceRequest

/**
 * [devices] is what puts a name on the asking device: it is trusted by the time it can ask, but
 * it may well be off the network while its request waits here, so the online list is not enough.
 */
fun IncomingSourceRequest.toUi(devices: List<TrustedDevice>): SyncRequestUi = SyncRequestUi(
    sourceId = sourceId,
    deviceName = devices
        .filter { it.deviceId == deviceId }
        .maxByOrNull { it.lastSeen }
        ?.displayName
        ?: deviceId,
    label = label,
    mode = syncMode.toUi(),
)

/** The mode as the send flow names it — one vocabulary for both halves of a source. */
fun SyncMode.toUi(): SourceModeUi = when (this) {
    SyncMode.Mirror -> SourceModeUi.Sync
    is SyncMode.AutoUpload -> SourceModeUi.AutoUpload
    is SyncMode.Offload -> SourceModeUi.Offload
}

/** How a registered location reads on screen. Null for one the receiving side never picks. */
fun SourceLocation.toHostLocationUi(): HostLocationUi? = when (this) {
    is SourceLocation.Internal -> HostLocationUi.AppStorage
    is SourceLocation.Tree -> HostLocationUi.Folder(uri = path, label = path)
    is SourceLocation.Directory -> HostLocationUi.Folder(uri = path, label = path)
    SourceLocation.Media,
    is SourceLocation.Root -> null
}
