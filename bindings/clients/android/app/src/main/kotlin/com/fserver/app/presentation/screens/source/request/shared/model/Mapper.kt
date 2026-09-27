package com.fserver.app.presentation.screens.source.request.shared.model

import com.fserver.app.presentation.screens.source.shared.model.latest
import com.fserver.app.presentation.screens.source.shared.model.toUi
import com.fserver.core.network.device.model.TrustedDevice
import com.fserver.core.sync.setup.IncomingSourceRequest

private const val FingerprintGroups = 2

/**
 * [devices] is what puts a name on the asking device: it is trusted by the time it can ask, but
 * it may well be off the network while its request waits here, so the online list is not enough.
 */
fun IncomingSourceRequest.toUi(devices: List<TrustedDevice>): SyncRequestUi {
    val device = devices.latest(deviceId)

    return SyncRequestUi(
        sourceId = sourceId,
        deviceName = device?.displayName ?: deviceId,
        label = label,
        mode = syncMode.toUi(),
        deviceKind = device?.metadata?.kind,
        fingerprint = device?.fingerprint?.groups?.take(FingerprintGroups)?.joinToString(" "),
        // TODO: IncomingSourceRequest does not carry the source's size yet - needs a protocol change.
        files = null,
        bytes = null,
    )
}
