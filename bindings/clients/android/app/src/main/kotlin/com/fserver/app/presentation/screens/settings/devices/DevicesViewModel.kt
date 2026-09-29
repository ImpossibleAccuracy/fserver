package com.fserver.app.presentation.screens.settings.devices

import com.fserver.app.util.stateInScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.composable.model.toPeerUi
import com.fserver.app.presentation.screens.settings.devices.model.DevicesState
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.device.model.ForeignDevice
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

/**
 * Connections and trust, on one screen because they are two views of the same device.
 *
 * "Trusted" here means remembered but without a session: a connected device is listed once, under
 * connected.
 *
 * Nothing on this screen acts on a device. Disconnecting and forgetting both live one level down,
 * on the device's own screen, where there is room to say what they do.
 */
class DevicesViewModel(
    devicesRepository: DevicesRepository,
) : ViewModel() {

    val state: StateFlow<DevicesState> = combine(
        devicesRepository.devices.connected,
        devicesRepository.devices.known,
    ) { connected, known ->
        DevicesState(
            connected = connected.map { it.toUi() },
            trusted = known.filterNot { it.hasSession }.map { it.toUi() },
        )
    }.stateInScreen(viewModelScope, DevicesState())
}

private fun ForeignDevice.toUi() = DevicesState.DeviceUi(
    peer = toPeerUi(),
    subtitle = routes.firstOrNull()?.address,
)
