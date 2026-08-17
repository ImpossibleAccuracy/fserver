package com.fserver.app.presentation.screens.settings.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.screens.settings.devices.model.DevicesState
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.device.model.ForeignDevice
import com.fserver.core.network.device.model.TrustedDevice
import com.fserver.core.store.TrustedDevicesStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Connections and trust, on one screen because they are two views of the same device.
 *
 * Trust is keyed by public key, not by device — a peer may rotate or present several. The list
 * folds them back to one row per device so the user sees machines, not key material, and drops the
 * ones already listed as connected: "trusted" here means remembered but not currently reachable.
 *
 * Nothing on this screen acts on a device. Disconnecting and forgetting both live one level down,
 * on the device's own screen, where there is room to say what they do.
 */
class DevicesViewModel(
    devicesRepository: DevicesRepository,
    trustedDevicesStore: TrustedDevicesStore,
) : ViewModel() {

    val state: StateFlow<DevicesState> = combine(
        devicesRepository.onlineDevices,
        trustedDevicesStore.devices,
    ) { online, trusted ->
        val connected = online.filter(ForeignDevice::hasSession)
        val connectedIds = connected.mapTo(mutableSetOf()) { it.deviceId }

        DevicesState(
            connected = connected.map { it.toUi() },
            trusted = trusted
                .distinctBy { it.deviceId }
                .filterNot { it.deviceId in connectedIds }
                .map { it.toUi(online) },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DevicesState(),
    )
}

private fun ForeignDevice.toUi() = DevicesState.DeviceUi(
    deviceId = deviceId,
    name = displayName,
    subtitle = routes.firstOrNull()?.address,
    kind = kind,
)

/** The kind is never persisted with the trust record, so it comes from the live list when there. */
private fun TrustedDevice.toUi(online: List<ForeignDevice>) = DevicesState.DeviceUi(
    deviceId = deviceId,
    name = displayName,
    subtitle = null,
    kind = online.firstOrNull { it.deviceId == deviceId }?.kind,
)
