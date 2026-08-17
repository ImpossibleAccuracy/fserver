package com.fserver.app.presentation.screens.settings.devices.model

import com.fserver.core.network.device.model.DeviceKind

/**
 * The two lists are disjoint: a connected device is listed once, under "connected now". Listing it
 * again under "trusted" would read as two devices, and the second row would say nothing the first
 * one does not.
 */
data class DevicesState(
    val connected: List<DeviceUi> = emptyList(),
    val trusted: List<DeviceUi> = emptyList(),
) {
    val isEmpty: Boolean get() = connected.isEmpty() && trusted.isEmpty()

    data class DeviceUi(
        val deviceId: String,
        val name: String,
        /** Its address while connected; nothing once it is only remembered. */
        val subtitle: String?,
        val kind: DeviceKind?,
    )
}
