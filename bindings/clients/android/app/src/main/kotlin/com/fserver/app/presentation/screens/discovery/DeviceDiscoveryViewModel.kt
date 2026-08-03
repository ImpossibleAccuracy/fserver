package com.fserver.app.presentation.screens.discovery

import androidx.lifecycle.ViewModel
import com.fserver.app.data.DemoContentSource
import com.fserver.app.presentation.screens.discovery.model.DeviceDiscoveryIntent
import com.fserver.app.presentation.screens.discovery.model.DeviceDiscoveryState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * mDNS discovery.
 *
 * Scanning never reaches a "finished" state: [com.fserver.app.presentation.screens.discovery.model.DeviceDiscoveryState.searching] stays true
 * so a network that blocks multicast never looks like an empty result, and the manual
 * routes stay reachable throughout.
 *
 * Offline devices are kept in the list rather than filtered out — the user needs to see
 * that a device exists and is unreachable right now.
 */
class DeviceDiscoveryViewModel(
    private val content: DemoContentSource,
) : ViewModel() {

    private val _state = MutableStateFlow(
        DeviceDiscoveryState(
            networkName = content.networkName(),
            devices = content.devices(),
        )
    )
    val state: StateFlow<DeviceDiscoveryState> = _state.asStateFlow()

    fun onIntent(intent: DeviceDiscoveryIntent) {
        when (intent) {
            DeviceDiscoveryIntent.RefreshClicked ->
                _state.value = _state.value.copy(devices = content.devices(), searching = true)
        }
    }
}
