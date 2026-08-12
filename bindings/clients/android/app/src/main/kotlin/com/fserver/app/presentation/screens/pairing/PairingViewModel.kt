package com.fserver.app.presentation.screens.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.model.address
import com.fserver.app.presentation.screens.pairing.model.PairingIntent
import com.fserver.app.presentation.screens.pairing.model.PairingState
import com.fserver.app.presentation.screens.pairing.model.PairingUiEffect
import com.fserver.core.domain.repository.DeviceDetectionRepository
import com.fserver.net.connection.ConnectionManager
import com.fserver.net.connection.PeerRef
import com.fserver.net.discovery.DiscoveredPeer
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds


@OptIn(FlowPreview::class)
class PairingViewModel(
    private val key: Destination.Pairing,
    private val connectionManager: ConnectionManager<Any>,
    private val deviceDetectionRepository: DeviceDetectionRepository,
) : ViewModel() {
    private val effects = Channel<PairingUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    private val password = MutableStateFlow("")
    private val rememberDevice = MutableStateFlow(true)
    private val probeError = MutableStateFlow<String?>(null)

    val state: StateFlow<PairingState> = combine(
        deviceDetectionRepository.device(key.deviceId).debounce(200.milliseconds),
        connectionManager.profiles.map { it[key.deviceId] },
        password,
        rememberDevice,
        probeError,
    ) { peer, profile, password, rememberDevice, error ->
        PairingState(
            device = deviceUi(peer, profile),
            password = password,
            rememberDevice = rememberDevice,
            error = error,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PairingState(),
        )

    init {
        restoreProfile()
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

    /**
     * Tries to connect to the device in three ways:
     * 1. Using the saved handshake, if any.
     * 2. Using the discovered peer, if any.
     * 3. Using the reconnection arguments, if any.
     * If all three fail, the error is stored in [probeError] and the function returns false.
     */
    private suspend fun connect(): Boolean = connectUsingSavedHandshake()
        .recoverCatching {
            connectUsingDiscoveredPeer().getOrThrow()
        }
        .recoverCatching {
            if (key.reconnectionArguments == null) throw it
            else tryReconnectByArguments().getOrThrow()
        }
        .fold(
            onSuccess = { true },
            onFailure = { e ->
                probeError.update { e.localizedMessage }
                false
            }
        )

    private suspend fun connectUsingSavedHandshake(): Result<Unit> =
        connectionManager.profile(key.deviceId)
            ?.let { profile ->
                connectionManager.connect(profile.route).map { }
            }
            ?: Result.failure(IllegalStateException("No saved handshake for ${key.deviceId}"))

    private suspend fun connectUsingDiscoveredPeer(): Result<Unit> {
        val peer = deviceDetectionRepository.device(key.deviceId).firstOrNull()
            ?: return Result.failure(IllegalStateException("No discovered peer for ${key.deviceId}"))

        return connectionManager.connect(peer).map { }
    }

    private suspend fun tryReconnectByArguments(): Result<Unit> {
        val endpoint = key.reconnectionArguments?.asEndpoint()
            ?: return Result.failure(IllegalStateException("No reconnection arguments for ${key.deviceId}"))

        val ref = PeerRef(
            deviceId = key.deviceId,
            transport = endpoint.transport,
            endpoint = endpoint,
        )

        return connectionManager.connect(ref).map { }
    }

    /**
     * Handshakes again when the screen came back to an empty cache - after process death, or when
     * the app was killed mid-flow. Nothing to do when discovery still knows the device, or when
     * the screen was opened from the discovery list and so carries no address of its own.
     */
    private fun restoreProfile() {
        val endpoint = key.reconnectionArguments?.asEndpoint() ?: return
        if (connectionManager.profile(key.deviceId) != null) return

        // The device is not in the cache, but discovery still knows it. No need to probe.
        if (deviceDetectionRepository.isDeviceOnline(key.deviceId)) return

        viewModelScope.launch {
            val ref = PeerRef(
                deviceId = key.deviceId,
                transport = endpoint.transport,
                endpoint = endpoint,
            )

            // The result lands in ConnectionManager.profiles, which `state` is already reading.
            connectionManager.probe(ref).onFailure { e ->
                // TODO: add error messages parser util
                probeError.update { e.localizedMessage }
            }
        }
    }
}

/**
 * The device as two sources see it.
 * The handshake wins wherever they overlap: it is the only one
 * that talked to the device, while discovered is whatever the network claimed.
 */
private fun deviceUi(
    peer: DiscoveredPeer?,
    profile: ConnectionManager.Profile?,
): PairingState.DeviceUi? {
    if (peer == null && profile == null) return null

    val fingerprint = profile?.identity?.fingerprint ?: peer?.advertised?.fingerprint

    return PairingState.DeviceUi(
        name = profile?.identity?.displayName ?: peer?.displayName.orEmpty(),
        kind = peer?.kind,
        access = peer?.advertised?.accessMode,
        address = profile?.route?.endpoint?.address ?: peer?.address.orEmpty(),
        // TODO: hardcoded strings, extract
        technicalLine = profile?.negotiated
            ?.let { "${it.cipherSuite.name} · protocol v${it.protocolVersion}" }
            .orEmpty(),
        fingerprintGroups = fingerprint?.value?.split(" ").orEmpty(),
    )
}
