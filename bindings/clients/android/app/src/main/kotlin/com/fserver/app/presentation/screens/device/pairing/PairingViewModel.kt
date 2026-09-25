package com.fserver.app.presentation.screens.device.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.R
import com.fserver.app.domain.AuthManager
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.model.UiText
import com.fserver.app.presentation.permission.forLocator
import com.fserver.app.presentation.screens.device.pairing.model.PairingIntent
import com.fserver.app.presentation.screens.device.pairing.model.PairingState
import com.fserver.app.presentation.screens.device.pairing.model.PairingUiEffect
import com.fserver.app.presentation.shared.error.AppError
import com.fserver.app.presentation.shared.error.ErrorReporter
import com.fserver.app.presentation.shared.error.toAppError
import com.fserver.core.Constants
import com.fserver.core.network.auth.AuthCredentials
import com.fserver.core.network.auth.AuthMethod
import com.fserver.core.network.auth.Greeting
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.device.model.ForeignDevice
import com.fserver.core.network.info.model.PeerLocator
import com.fserver.core.requirement.RequirementsChecker
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
    private val authManager: AuthManager,
    private val requirementsChecker: RequirementsChecker,
    private val reporter: ErrorReporter,
) : ViewModel() {
    /** Only a [PeerLocator.DiscoveredDevice] has one before a session exists. */
    private val knownDeviceId: String? =
        (key.peerLocator as? PeerLocator.DiscoveredDevice)?.id

    private val effects = Channel<PairingUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    private val editable = MutableStateFlow(Editable())
    private val selectedMethod = MutableStateFlow<AuthMethod?>(null)

    /** The result of [probe] — versions and methods, nothing trusted yet. */
    private val greeting = MutableStateFlow<Greeting?>(null)

    private val device: Flow<ForeignDevice?> = knownDeviceId
        ?.let { id -> devicesRepository.devices.device(id).debounce(200.milliseconds) }
        ?: flowOf(null)

    val state: StateFlow<PairingState> = combine(
        device,
        greeting,
        selectedMethod,
        editable,
    ) { device, greeting, selectedMethod, editable ->
        PairingState(
            device = deviceUi(device, greeting, selectedMethod),
            rememberDevice = editable.rememberDevice,
            isConnecting = editable.isConnecting,
            password = editable.password,
            error = editable.error,
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
                devicesRepository.devices.device(id).filterNotNull().first { it.hasSession }
                ensureLoggedIn()
                effects.send(PairingUiEffect.NavigateNext)
            }
        }
    }

    fun onIntent(intent: PairingIntent) {
        when (intent) {
            is PairingIntent.RememberDeviceChanged -> editable.update { it.copy(rememberDevice = intent.remember) }
            is PairingIntent.MethodSelected -> selectedMethod.update { intent.method }

            PairingIntent.Connect -> viewModelScope.launch {
                editable.update { it.copy(isConnecting = true) }
                val connected = connect()
                editable.update { it.copy(isConnecting = false) }

                if (connected) {
                    ensureLoggedIn()
                    effects.send(PairingUiEffect.NavigateNext)
                }
            }

            is PairingIntent.UpdatePassword -> editable.update {
                it.copy(password = intent.password)
            }
        }
    }

    private suspend fun connect(): Boolean {
        if (!requirementsMet()) return false

        return devicesRepository
            .connect(arguments = key.peerLocator, credentials = credentials())
            .onFailure { t -> editable.update { it.copy(error = t.toAppError()) } }
            .isSuccess
    }

    /**
     * Whatever the OS still wants before this locator can be dialled at all. Reported through the
     * requirements sheet rather than the line under the button: a permission is a question, and
     * the field below the button is for answers the device gave.
     */
    private suspend fun requirementsMet(): Boolean {
        val requirements = requirementsChecker.forLocator(key.peerLocator)
        if (requirements.isSatisfied) return true

        reporter.report(requirements)
        return false
    }

    private fun credentials(): AuthCredentials? = when (selectedMethod.value) {
        AuthMethod.ConfirmFingerprint -> AuthCredentials.ConfirmFingerprint
        AuthMethod.NearbySas -> AuthCredentials.NearbySas
        AuthMethod.Password -> AuthCredentials.Password(editable.value.password.orEmpty())
        null -> null
    }

    private suspend fun ensureLoggedIn() {
        authManager.ensureLoggedIn()
    }

    /**
     * Ensure the greeting for [key] is fetched. Pairing screen shouldn't trust advertised info
     * (like fingerprint) — only what this returns.
     */
    private fun probe() {
        viewModelScope.launch {
            editable.update { it.copy(error = null) }

            if (!requirementsMet()) return@launch

            devicesRepository.probe(key.peerLocator).fold(
                onSuccess = { result ->
                    greeting.value = result
                    // Nothing picked yet: default to the one method the device offered, so the
                    // button is actionable without an extra tap when there is no real choice.
                    selectedMethod.update { it ?: result.methods.firstOrNull() }
                },
                onFailure = { t ->
                    editable.update {
                        it.copy(
                            error = AppError(
                                message = UiText.of(R.string.pairing_probe_failed),
                                detail = t.toAppError().message,
                            )
                        )
                    }
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

        val address = device?.routes?.firstOrNull()?.address
            ?: (key.peerLocator as? PeerLocator.Ip)?.let {
                "${it.host}:${it.port ?: Constants.DEFAULT_PORT}"
            }

        return PairingState.DeviceUi(
            identity = identity,
            address = address,
            protocolLine = greeting?.protocolVersions?.let(::protocolLine),
            offeredMethods = greeting?.methods.orEmpty(),
            selectedMethod = selectedMethod,
            // The greeting proves nothing; only a real session's identity is worth comparing.
            fingerprintGroups = device?.handshake?.fingerprint
                ?.split(" ")
                .orEmpty(),
        )
    }

    private fun protocolLine(versions: IntRange): UiText =
        if (versions.first == versions.last) {
            UiText.of(R.string.pairing_protocol_one, versions.first)
        } else {
            UiText.of(R.string.pairing_protocol_range, versions.first, versions.last)
        }
}

private data class Editable(
    val rememberDevice: Boolean = false,
    val isConnecting: Boolean = false,
    val password: String? = null,
    val error: AppError? = null,
)
