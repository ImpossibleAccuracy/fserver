package com.fserver.app.presentation.screens.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.domain.model.DeviceConnectionCapabilities
import com.fserver.app.domain.model.FoundDevice
import com.fserver.app.domain.repository.DeviceDetectionRepository
import com.fserver.app.presentation.model.address
import com.fserver.app.presentation.screens.pairing.model.PairingIntent
import com.fserver.app.presentation.screens.pairing.model.PairingState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds


@OptIn(FlowPreview::class)
class PairingViewModel(
    deviceId: String,
    deviceDetectionRepository: DeviceDetectionRepository,
) : ViewModel() {

    private val password = MutableStateFlow("")
    private val rememberDevice = MutableStateFlow(true)
    private val capabilities = MutableStateFlow<DeviceConnectionCapabilities?>(null)

    val state: StateFlow<PairingState> = combine(
        deviceDetectionRepository.device(deviceId).debounce(200.milliseconds),
        capabilities,
        password,
        rememberDevice,
    ) { device, capabilities, password, rememberDevice ->
        PairingState(
            device = if (device == null || capabilities == null) null
            else device.toUi(capabilities),
            password = password,
            rememberDevice = rememberDevice,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PairingState(),
        )

    init {
        viewModelScope.launch {
            deviceDetectionRepository
                .checkConnectionCapabilities(deviceId)
                .fold(
                    onSuccess = { data ->
                        capabilities.update { data }
                    },
                    onFailure = {
                        // TODO: show UI error
                    }
                )
        }
    }

    fun onIntent(intent: PairingIntent) {
        when (intent) {
            is PairingIntent.RememberDeviceChanged -> rememberDevice.update { intent.remember }
            is PairingIntent.PasswordChanged -> password.update { intent.password }
        }
    }
}

private fun FoundDevice.toUi(capabilities: DeviceConnectionCapabilities) = PairingState.DeviceUi(
    name = name,
    kind = kind,
    access = capabilities.access,
    address = address,
    technicalLine = "${capabilities.tlsVersion.name} · protocol ${capabilities.protocolVersion.name}",
    fingerprintGroups = capabilities.fingerprints.map { it.key },
)
