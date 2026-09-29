package com.fserver.app.presentation.screens.source.request.shared.model

import com.fserver.app.presentation.composable.model.peerOf
import com.fserver.app.presentation.composable.model.PeerUi
import com.fserver.app.presentation.screens.source.shared.model.toUi
import com.fserver.core.network.device.model.TrustedDevice
import com.fserver.core.sync.setup.IncomingSourceRequest

private const val FingerprintGroups = 2

/** [trusted] supplies the fingerprint: the asking device is trusted by the time it can ask. */
fun IncomingSourceRequest.toUi(
    peers: Map<String, PeerUi>,
    trusted: List<TrustedDevice>,
): SyncRequestUi {
    val peer = peers.peerOf(deviceId)
    val record = trusted.firstOrNull { it.deviceId == deviceId }

    return SyncRequestUi(
        sourceId = sourceId,
        deviceName = peer.name,
        label = label,
        mode = syncMode.toUi(),
        deviceKind = peer.kind,
        fingerprint = record?.fingerprint?.groups?.take(FingerprintGroups)?.joinToString(" "),
        // TODO: IncomingSourceRequest does not carry the source's size yet - needs a protocol change.
        files = null,
        bytes = null,
    )
}
