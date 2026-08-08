package com.fserver.app.presentation.screens.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.data.DemoContentSource
import com.fserver.app.domain.model.FoundDevice
import com.fserver.app.domain.model.address
import com.fserver.app.domain.repository.DeviceDetectionRepository
import com.fserver.app.presentation.model.HandshakeUi
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
import kotlin.time.Duration.Companion.milliseconds


@OptIn(FlowPreview::class)
class PairingViewModel(
    deviceId: String,
    deviceDetectionRepository: DeviceDetectionRepository,
    content: DemoContentSource,
) : ViewModel() {

    private val password = MutableStateFlow("")
    private val rememberDevice = MutableStateFlow(true)

    val state: StateFlow<PairingState> = combine(
        deviceDetectionRepository.device(deviceId).debounce(200.milliseconds),
        password,
        rememberDevice,
    ) { device, password, rememberDevice ->
        PairingState(
            device = device?.toUi(content.handshake(deviceId)),
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

private fun FoundDevice.toUi(handshake: HandshakeUi) = PairingState.DeviceUi(
    name = name,
    kind = kind,
    access = access,
    address = address,
    technicalLine = handshake.technicalLine,
    fingerprintGroups = handshake.fingerprintGroups,
)
