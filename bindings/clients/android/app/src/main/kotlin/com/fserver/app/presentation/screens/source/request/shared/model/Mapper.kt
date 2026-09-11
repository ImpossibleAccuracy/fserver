package com.fserver.app.presentation.screens.source.request.shared.model

import com.fserver.app.presentation.screens.source.shared.model.nameOf
import com.fserver.app.presentation.screens.source.shared.model.toUi
import com.fserver.core.network.device.model.TrustedDevice
import com.fserver.core.sync.setup.IncomingSourceRequest

/**
 * [devices] is what puts a name on the asking device: it is trusted by the time it can ask, but
 * it may well be off the network while its request waits here, so the online list is not enough.
 */
fun IncomingSourceRequest.toUi(devices: List<TrustedDevice>): SyncRequestUi = SyncRequestUi(
    sourceId = sourceId,
    deviceName = devices.nameOf(deviceId),
    label = label,
    mode = syncMode.toUi(),
)
