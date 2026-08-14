package com.fserver.app.presentation.screens.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.pairing.model.PairingIntent
import com.fserver.app.presentation.screens.pairing.model.PairingState
import com.fserver.app.presentation.screens.pairing.model.PairingUiEffect
import com.fserver.core.domain.Constants
import com.fserver.core.domain.model.AuthMethod
import com.fserver.core.domain.model.ConnectionArguments
import com.fserver.core.domain.model.ForeignDevice
import com.fserver.core.domain.model.Greeting
import com.fserver.core.domain.repository.DevicesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
    /** Only a [ConnectionArguments.DiscoveredDevice] has one before a session exists. */
    private val knownDeviceId: String? =
        (key.connectionArguments as? ConnectionArguments.DiscoveredDevice)?.id

    private val effects = Channel<PairingUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    private val rememberDevice = MutableStateFlow(true)
    private val selectedMethod = MutableStateFlow<AuthMethod?>(null)
    private val isConnecting = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    /** The result of [probe] — versions and methods, nothing trusted yet. */
    private val greeting = MutableStateFlow<Greeting?>(null)

    private val device: Flow<ForeignDevice?> = knownDeviceId
        ?.let { id -> devicesRepository.device(id).debounce(200.milliseconds) }
        ?: flowOf(null)

    private val flags = combine(rememberDevice, isConnecting, error, ::Flags)

    val state: StateFlow<PairingState> = combine(
        device,
        greeting,
        selectedMethod,
        flags,
    ) { device, greeting, selectedMethod, flags ->
        PairingState(
            device = deviceUi(device, greeting, selectedMethod),
            rememberDevice = flags.rememberDevice,
            isConnecting = flags.isConnecting,
            error = flags.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PairingState(),
    )

    init {
        probe()

        // A session for this device may show up on its own (background reconnect); only
        // meaningful once there is a known id to watch, which manual/QR targets don't have yet.
        knownDeviceId?.let { id ->
            viewModelScope.launch {
                devicesRepository.device(id).filterNotNull().first { it.hasSession }
                effects.send(PairingUiEffect.NavigateFiles)
            }
        }
    }

    fun onIntent(intent: PairingIntent) {
        when (intent) {
            is PairingIntent.RememberDeviceChanged -> rememberDevice.update { intent.remember }
            is PairingIntent.MethodSelected -> selectedMethod.update { intent.method }

            PairingIntent.Connect -> viewModelScope.launch {
                isConnecting.update { true }
                val connected = connect()
                isConnecting.update { false }

                if (connected) {
                    effects.send(PairingUiEffect.NavigateFiles)
                }
            }
        }
    }

    private suspend fun connect(): Boolean = devicesRepository
        .connect(key.connectionArguments, selectedMethod.value)
        .onFailure { t -> error.update { t.localizedMessage } }
        .isSuccess

    /**
     * Ensure the greeting for [key] is fetched. Pairing screen shouldn't trust advertised info
     * (like fingerprint) — only what this returns.
     */
    private fun probe() {
        viewModelScope.launch {
            error.update { null }

            devicesRepository.probe(key.connectionArguments).fold(
                onSuccess = { result ->
                    greeting.value = result
                    // Nothing picked yet: default to the one method the device offered, so the
                    // button is actionable without an extra tap when there is no real choice.
                    selectedMethod.update { it ?: result.methods.firstOrNull() }
                },
                onFailure = { t ->
                    // TODO: add error messages parser util
                    error.update { t.localizedMessage }
                }
            )
        }
    }

    private fun deviceUi(
        device: ForeignDevice?,
        greeting: Greeting?,
        selectedMethod: AuthMethod?,
    ): PairingState.DeviceUi? {
        // Nothing to show yet: neither the greeting nor a cached discovery entry has arrived.
        if (device == null && greeting == null) return null

        val identity = device?.let {
            PairingState.DeviceUi.IdentityUi(name = it.displayName, kind = it.kind)
        }

        val address = device?.routes?.firstOrNull()?.endpoint?.address
            ?: (key.connectionArguments as? ConnectionArguments.Ip)?.let {
                "${it.host}:${it.port ?: Constants.DEFAULT_PORT}"
            }

        return PairingState.DeviceUi(
            identity = identity,
            address = address,
            protocolLine = greeting?.protocolVersions?.let(::protocolLine).orEmpty(),
            offeredMethods = greeting?.methods.orEmpty(),
            selectedMethod = selectedMethod,
            // The greeting proves nothing; only a real session's identity is worth comparing.
            fingerprintGroups = device?.handshake?.identity?.fingerprint?.value
                ?.split(" ")
                .orEmpty(),
        )
    }

    private fun protocolLine(versions: IntRange): String = if (versions.first == versions.last) {
        "protocol v${versions.first}"
    } else {
        "protocol v${versions.first}–${versions.last}"
    }

    private data class Flags(
        val rememberDevice: Boolean,
        val isConnecting: Boolean,
        val error: String?,
    )
}
