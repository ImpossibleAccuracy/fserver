package com.fserver.app.presentation.screens.discovery.automatic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.core.domain.model.DetectionMethod
import com.fserver.core.domain.model.DeviceDetectionRequest
import com.fserver.core.domain.model.FoundDevice
import com.fserver.core.domain.model.NetworkInfo
import com.fserver.core.domain.model.availableDetectionMethods
import com.fserver.core.domain.repository.DeviceDetectionRepository
import com.fserver.core.domain.repository.NetworkInfoRepository
import com.fserver.app.presentation.model.address
import com.fserver.app.presentation.screens.discovery.automatic.model.DeviceDiscoveryIntent
import com.fserver.app.presentation.screens.discovery.automatic.model.DeviceDiscoveryState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    kind = kind,
    address = address,
    online = true,
)
