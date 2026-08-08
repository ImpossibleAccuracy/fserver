package com.fserver.app.presentation.screens.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.domain.model.FoundDeviceDomain
import com.fserver.app.domain.repository.DeviceDetectionRepository
import com.fserver.app.domain.repository.NetworkInfoRepository
import com.fserver.app.presentation.screens.discovery.model.DeviceDiscoveryIntent
import com.fserver.app.presentation.screens.discovery.model.DeviceDiscoveryState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * mDNS discovery.
 *
 * Scanning never reaches a "finished" state: [DeviceDiscoveryState.searching] stays true
 * so a network that blocks multicast never looks like an empty result, and the manual
 * routes stay reachable throughout.
 *
 * Offline devices are kept in the list rather than filtered out — the user needs to see
 * that a device exists and is unreachable right now.
 */
class DeviceDiscoveryViewModel(
    private val networkInfoRepository: NetworkInfoRepository,
    private val deviceDetectionRepository: DeviceDetectionRepository,
) : ViewModel() {

    val state: StateFlow<DeviceDiscoveryState> = combine(
        networkInfoRepository.networkInfo,
        deviceDetectionRepository.onlineDevices,
        deviceDetectionRepository.isScanning,
    ) { networkInfo, onlineDevices, isScanning ->
        DeviceDiscoveryState(
            network = networkInfo?.let {
                DeviceDiscoveryState.NetworkInfo(
                    name = it.name
                )
            },
            devices = onlineDevices.map {
                DeviceDiscoveryState.Device(
                    id = it.id,
                    name = it.name,
                    address = when (it.source) {
                        is FoundDeviceDomain.Source.ManualEntry -> "${it.source.ipAddress}:${it.source.port}"
                        is FoundDeviceDomain.Source.NearbyDevice -> it.source.deviceId
                        is FoundDeviceDomain.Source.NetworkServiceDiscovery -> "${it.source.serviceName}.${it.source.serviceType}.${it.source.domain}"
                        is FoundDeviceDomain.Source.SubnetScan -> "${it.source.ipAddress}:${it.source.port}"
                    },
                    online = true,
                )
            },
            searching = isScanning,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DeviceDiscoveryState()
        )

    init {
        viewModelScope.launch {
            deviceDetectionRepository.startAndroidDiscovery()
            deviceDetectionRepository.startNetworkServiceDiscovery()
        }
    }

    fun onIntent(intent: DeviceDiscoveryIntent) {
        when (intent) {
            DeviceDiscoveryIntent.RefreshClicked -> {

            }
        }
    }
}
