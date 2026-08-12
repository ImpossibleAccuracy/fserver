package com.fserver.app.presentation.screens.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.pairing.model.PairingIntent
import com.fserver.app.presentation.screens.pairing.model.PairingState
import com.fserver.app.presentation.screens.pairing.model.PairingTarget
import com.fserver.app.presentation.screens.pairing.model.PairingUiEffect
import com.fserver.core.data.utils.chainWith
import com.fserver.core.domain.model.ForeignDevice
import com.fserver.core.domain.repository.DevicesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds


@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class PairingViewModel(
    private val key: Destination.Pairing,
    private val devicesRepository: DevicesRepository,
) : ViewModel() {
    private val deviceId = MutableStateFlow(key.deviceId)

    private val effects = Channel<PairingUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    private val password = MutableStateFlow("")
    private val rememberDevice = MutableStateFlow(true)
    private val probeError = MutableStateFlow<String?>(null)

    private val device = deviceId
        .flatMapLatest { devicesRepository.device(it) }
        .debounce(200.milliseconds)

    val state: StateFlow<PairingState> = combine(
        device,
        password,
        rememberDevice,
        probeError,
    ) { device, password, rememberDevice, error ->
        PairingState(
            device = deviceUi(device),
            password = password,
            rememberDevice = rememberDevice,
            error = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PairingState(),
    )

    init {
        ensureHandshake()

        viewModelScope.launch {
            // Wait for the device to be connected, then navigate to the files screen
            device.filterNotNull().first { it.hasSession }
            effects.send(PairingUiEffect.NavigateFiles)
        }
    }

    fun onIntent(intent: PairingIntent) {
        when (intent) {
            is PairingIntent.RememberDeviceChanged -> rememberDevice.update { intent.remember }
            is PairingIntent.PasswordChanged -> password.update { intent.password }

            PairingIntent.Connect -> viewModelScope.launch {
                if (connect()) {
                    effects.send(
                        PairingUiEffect.NavigateFiles
                    )
                }
            }
        }
    }

    // TODO: pass auth to `connect`
    private suspend fun connect(): Boolean =
        devicesRepository.connect(deviceId.value)
            .onFailure { t ->
                probeError.update { t.localizedMessage }
            }
            .isSuccess

    /**
     * Ensure handshake performed for [key] device.
     * Pairing screen shouldn't trust advertised info (like fingerprint).
     */
    private fun ensureHandshake() {
        val id = deviceId.value
        val arguments = key.reconnectionArguments

        viewModelScope.launch {
            // First, try handshake by deviceId
            devicesRepository.handshakeByDeviceId(id)
                .chainWith {
                    // If failed, try handshake by connection arguments (IP or QR payload)
                    when (arguments) {
                        null -> null

                        is PairingTarget.ConnectionArguments.Ip ->
                            devicesRepository.handshake(arguments.host, arguments.port)

                        is PairingTarget.ConnectionArguments.QrPayload ->
                            devicesRepository.handshake(arguments.payload)
                    }
                }
                ?.fold(
                    onSuccess = {
                        // Device ID changed after handshake, update it to new one
                        deviceId.value = it.descriptor.deviceId
                    },
                    onFailure = { t ->
                        // TODO: add error messages parser util
                        probeError.update { t.localizedMessage }
                    }
                )
        }
    }
}

private fun deviceUi(device: ForeignDevice?): PairingState.DeviceUi? {
    if (device == null) return null

    val handshake = device.handshake

    return PairingState.DeviceUi(
        name = device.descriptor.displayName,
        kind = device.descriptor.kind,
        access = device.descriptor.accessMode,
        address = device.routes.first().endpoint.address,
        // TODO: hardcoded strings, extract
        technicalLine = device.descriptor.advertised
            .let { "${it.dictionaryId} · protocol v${it.dictionaryVersion}" },
        fingerprintGroups = handshake?.identity?.fingerprint?.value?.split(" ")
            .orEmpty()
    )
}
