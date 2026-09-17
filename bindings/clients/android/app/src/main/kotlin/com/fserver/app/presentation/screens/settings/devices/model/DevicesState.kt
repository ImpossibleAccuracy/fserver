package com.fserver.app.presentation.screens.settings.devices.model

import com.fserver.core.network.TransportKind
import com.fserver.core.network.device.model.DeviceKind

/**
 * The two lists are disjoint: a connected device is listed once, under "connected now". Listing it
 * again under "trusted" would read as two devices, and the second row would say nothing the first
 * one does not.
 */
data class DevicesState(
    val connected: List<DeviceUi> = emptyList(),
    val trusted: List<DeviceUi> = emptyList(),
    val invitation: InvitationUi = InvitationUi.Loading,
) {
    val isEmpty: Boolean get() = connected.isEmpty() && trusted.isEmpty()

    /**
     * This device's own connection code. [Loading] is the state the sheet opens in: the code takes
     * a listener and an identity read to assemble, and an empty sheet reads as a broken one.
     */
    sealed interface InvitationUi {
        data object Loading : InvitationUi

        data object Unavailable : InvitationUi

        data class Ready(
            val payload: String,
            val addresses: List<AddressUi>,
            val fingerprintGroups: List<String>,
        ) : InvitationUi
    }

    data class AddressUi(
        val address: String,
        val transport: TransportKind?,
    )

    data class DeviceUi(
        val deviceId: String,
        val name: String,
        /** Its address while connected; nothing once it is only remembered. */
        val subtitle: String?,
        val kind: DeviceKind?,
    )
}
