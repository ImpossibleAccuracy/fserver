package com.fserver.app.presentation.screens.settings.mydevice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.screens.settings.mydevice.model.MyDeviceIntent
import com.fserver.app.presentation.screens.settings.mydevice.model.MyDeviceState
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.device.model.DeviceInvitation
import com.fserver.core.storage.DeviceIdentityRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How this phone presents itself to others: its name and its connection code. */
class MyDeviceViewModel(
    devicesRepository: DevicesRepository,
    private val identity: DeviceIdentityRepository,
) : ViewModel() {

    val state: StateFlow<MyDeviceState> = combine(
        identity.localDevice,
        devicesRepository.advertising.invitation,
    ) { device, invitation ->
        MyDeviceState(
            name = device.displayName,
            invitation = invitation?.toUi() ?: MyDeviceState.InvitationUi.Unavailable,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MyDeviceState(),
    )

    fun onIntent(intent: MyDeviceIntent) {
        when (intent) {
            is MyDeviceIntent.Renamed -> viewModelScope.launch {
                identity.setDisplayName(intent.name)
            }
        }
    }
}

private fun DeviceInvitation.toUi() = MyDeviceState.InvitationUi.Ready(
    payload = payload,
    addresses = routes.map {
        MyDeviceState.AddressUi(address = it.address, transport = it.transport)
    },
    fingerprintGroups = fingerprint.groups,
)
