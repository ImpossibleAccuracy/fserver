package com.fserver.app.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.domain.model.DetectionMethod
import com.fserver.app.domain.model.DeviceDetectionRequest
import com.fserver.app.domain.model.availableDetectionMethods
import com.fserver.app.domain.repository.DeviceDetectionRepository
import com.fserver.app.domain.repository.NetworkInfoRepository
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.model.NavigationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AppViewModel(
    private val networkInfoRepository: NetworkInfoRepository,
    private val deviceDetectionRepository: DeviceDetectionRepository,
) : ViewModel() {
    // Should listen to auth and navigation states
    private val isAllowedToSearchDevices = MutableStateFlow(true)

    private val _state = MutableStateFlow(
        NavigationState(
            startDestination = Destination.Onboarding
        )
    )
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Switching Wi-Fi -> mobile changes what is even possible, so the pass restarts
            // against the new capability set rather than leaving dead scans running.
            networkInfoRepository.networkInfo
                .map { it.availableDetectionMethods() }
                .distinctUntilChanged()
                .combine(isAllowedToSearchDevices) { available, allowed ->
                    if (allowed) available else emptySet()
                }
                .collect { startAutomaticDetection(it) }
        }
    }

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