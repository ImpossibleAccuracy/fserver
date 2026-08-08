package com.fserver.app.presentation.screens.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.domain.model.DetectionMethod
import com.fserver.app.domain.model.DeviceDetectionRequest
import com.fserver.app.domain.model.FoundDevice
import com.fserver.app.domain.model.NetworkInfo
import com.fserver.app.domain.model.availableDetectionMethods
import com.fserver.app.domain.repository.DeviceDetectionRepository
import com.fserver.app.domain.repository.NetworkInfoRepository
import com.fserver.app.presentation.screens.discovery.model.DeviceDiscoveryIntent
import com.fserver.app.presentation.screens.discovery.model.DeviceDiscoveryState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Device discovery.
 *
 * Which methods may run is not a decision this screen makes: it falls out of the
 * transport's [com.fserver.app.domain.model.NetworkCapability] set via
 * [availableDetectionMethods]. On Wi-Fi that is everything; on mobile data only the
 * radio-based nearby API and a manually entered address survive, because carrier NAT
 * makes a subnet sweep or an mDNS query cost data and return nothing.
 *
 * Only [DetectionMethod.Automatic] methods start on their own. A subnet sweep is slow and
 * loud, so it is offered as an escape hatch after the automatic pass finishes empty —
 * which is also the case where a multicast-filtering router would otherwise leave the user
 * staring at a permanently empty list.
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
        deviceDetectionRepository.runningScanningMethods,
    ) { networkInfo, onlineDevices, scanningMethods ->
        DeviceDiscoveryState(
            network = networkInfo?.toUi(),
            devices = onlineDevices.map { it.toUi() },
            // Not `networkInfo?.let { … }`: with no network at all the nearby-devices API
            // still works, and the extension already says so.
            detectionMethods = networkInfo.availableDetectionMethods().map { method ->
                DeviceDiscoveryState.DetectionMethodUi(
                    method = method,
                    isSearching = method in scanningMethods,
                )
            },
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DeviceDiscoveryState()
        )

    init {
        viewModelScope.launch {
            // Switching Wi-Fi → mobile changes what is even possible, so the pass restarts
            // against the new capability set rather than leaving dead scans running.
            networkInfoRepository.networkInfo
                .map { it.availableDetectionMethods() }
                .distinctUntilChanged()
                .collect { startAutomaticDetection(it) }
        }
    }

    fun onIntent(intent: DeviceDiscoveryIntent) {
        when (intent) {
            DeviceDiscoveryIntent.RefreshClicked -> viewModelScope.launch {
                startAutomaticDetection(currentlyAvailableMethods())
            }

            DeviceDiscoveryIntent.ScanSubnetClicked -> viewModelScope.launch {
                if (DetectionMethod.OnDemand.SubnetScan !in currentlyAvailableMethods()) return@launch

                deviceDetectionRepository.startDetection(
                    DeviceDetectionRequest.ByMethod(
                        DetectionMethod.OnDemand.SubnetScan
                    )
                )
            }
        }
    }

    private suspend fun currentlyAvailableMethods(): Set<DetectionMethod> =
        networkInfoRepository.networkInfo.first().availableDetectionMethods()

    /**
     * One coroutine per method: they run for very different lengths of time, and a slow one
     * must not hold back the results of a fast one.
     */
    private fun startAutomaticDetection(available: Set<DetectionMethod>) {
        available.filterIsInstance<DetectionMethod.Automatic>().forEach { method ->
            viewModelScope.launch {
                deviceDetectionRepository.startDetection(
                    DeviceDetectionRequest.ByMethod(method)
                )
            }
        }
    }
}

private fun NetworkInfo.toUi() = DeviceDiscoveryState.NetworkInfoUi(
    name = name,
    type = when (this) {
        is NetworkInfo.Mobile -> DeviceDiscoveryState.NetworkInfoUi.Type.Mobile
        is NetworkInfo.WiFi -> DeviceDiscoveryState.NetworkInfoUi.Type.WiFi
    }
)

private fun FoundDevice.toUi() = DeviceDiscoveryState.DeviceUi(
    id = id,
    name = name,
    address = when (source) {
        is FoundDevice.Source.ManualEntry -> "${source.ipAddress}:${source.port}"
        is FoundDevice.Source.NearbyDevice -> source.deviceId
        is FoundDevice.Source.NetworkServiceDiscovery ->
            "${source.serviceName}.${source.serviceType}.${source.domain}"

        is FoundDevice.Source.SubnetScan -> "${source.ipAddress}:${source.port}"
    },
    online = true,
)
