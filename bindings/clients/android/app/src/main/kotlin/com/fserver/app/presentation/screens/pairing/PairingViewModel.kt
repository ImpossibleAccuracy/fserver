package com.fserver.app.presentation.screens.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.model.address
import com.fserver.app.presentation.screens.pairing.model.PairingIntent
import com.fserver.app.presentation.screens.pairing.model.PairingState
import com.fserver.core.domain.repository.DeviceDetectionRepository
import com.fserver.net.discovery.DiscoveredPeer
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlin.time.Duration.Companion.milliseconds


@OptIn(FlowPreview::class)
class PairingViewModel(
    deviceId: String,
    deviceDetectionRepository: DeviceDetectionRepository,
) : ViewModel() {

    private val password = MutableStateFlow("")
    private val rememberDevice = MutableStateFlow(true)

    val state: StateFlow<PairingState> = combine(
        deviceDetectionRepository.device(deviceId).debounce(200.milliseconds),
        password,
        rememberDevice,
    ) { device, password, rememberDevice ->
        PairingState(
            device = device?.toUi(),
            password = password,
            rememberDevice = rememberDevice,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PairingState(),
        )

    fun onIntent(intent: PairingIntent) {
        when (intent) {
            is PairingIntent.RememberDeviceChanged -> rememberDevice.update { intent.remember }
            is PairingIntent.PasswordChanged -> password.update { intent.password }
        }
    }
}

private fun DiscoveredPeer.toUi() = PairingState.DeviceUi(
    name = displayName,
    kind = kind,
    access = advertised.accessMode,
    address = address,
    // TODO
    technicalLine = "TODO", //"${capabilities.tlsVersion.name} · protocol ${capabilities.protocolVersion.name}",
    fingerprintGroups = listOf(), //capabilities.fingerprints.map { it.key },
)
