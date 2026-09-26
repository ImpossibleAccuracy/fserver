package com.fserver.app.presentation.screens.settings.storage.main.model

import com.fserver.app.presentation.screens.source.shared.model.latest
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.storage.TrustedDevicesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

fun peers(
    trustedDevices: TrustedDevicesRepository,
    devicesRepository: DevicesRepository,
): Flow<Map<String, PeerUi>> = combine(
    trustedDevices.devices,
    devicesRepository.devices.connected,
) { trusted, connected ->
    val remembered = trusted.map { it.deviceId }.distinct().associateWith { deviceId ->
        val record = trusted.latest(deviceId)
        PeerUi(name = record?.displayName ?: deviceId, kind = record?.metadata?.kind)
    }

    remembered + connected.associate { it.deviceId to PeerUi(it.displayName, it.kind) }
}

fun Map<String, PeerUi>.peerOf(deviceId: String): PeerUi = this[deviceId] ?: PeerUi(name = deviceId)
