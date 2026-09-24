package com.fserver.app.presentation.screens.settings.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.composable.model.labelRes
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.settings.details.model.DeviceDetailsIntent
import com.fserver.app.presentation.screens.settings.details.model.DeviceDetailsState
import com.fserver.app.presentation.screens.settings.details.model.DeviceDetailsUiEffect
import com.fserver.app.presentation.screens.source.shared.model.latest
import com.fserver.app.presentation.shared.error.ErrorReporter
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.info.model.PeerLocator
import com.fserver.core.storage.TrustedDevicesRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Instant
import java.time.Instant as JavaInstant

/**
 * One device: its live session, and what the last completed handshake left behind.
 *
 * Forgetting drops every key recorded for the device, not just the one it last used — a device
 * left half-trusted would reconnect without a prompt through a key the user thought was gone.
 */
class DeviceDetailsViewModel(
    private val key: Destination.Settings.DeviceDetails,
    private val devicesRepository: DevicesRepository,
    private val trustedDevices: TrustedDevicesRepository,
    private val reporter: ErrorReporter,
) : ViewModel() {

    private val effects = Channel<DeviceDetailsUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    private val trustedKeys = trustedDevices.devices
        .map { records -> records.filter { it.deviceId == key.deviceId } }

    val state: StateFlow<DeviceDetailsState> = combine(
        devicesRepository.devices.device(key.deviceId),
        trustedDevices.observeKnownRoute(key.deviceId),
        trustedKeys,
    ) { device, knownRoute, trusted ->
        // Records of one device share their metadata, so any key of it answers for the device.
        val record = trusted.latest()

        DeviceDetailsState(
            name = device?.displayName ?: record?.displayName.orEmpty(),
            isConnected = device?.hasSession == true,
            address = device?.routes?.firstOrNull()?.address ?: knownRoute?.address,
            fingerprintGroups = device?.handshake?.fingerprint?.split(" ")
                ?: record?.fingerprint?.groups.orEmpty(),
            lastSeen = record?.metadata?.lastSeen?.formatted(),
            methodLabel = record?.method?.labelRes,
            protocolVersion = device?.handshake?.protocolVersion,
            isTrusted = record != null,
            isRouteKnown = knownRoute != null,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DeviceDetailsState(),
    )

    fun onIntent(intent: DeviceDetailsIntent) {
        when (intent) {
            DeviceDetailsIntent.DisconnectClicked -> viewModelScope.launch {
                devicesRepository.disconnect(key.deviceId)
                    .onFailure { reporter.report(it, "could not disconnect ${key.deviceId}") }
            }

            DeviceDetailsIntent.Reconnect -> viewModelScope.launch {
                devicesRepository.probe(PeerLocator.KnownDevice(key.deviceId))
                    .fold(
                        onSuccess = {
                            effects.send(
                                DeviceDetailsUiEffect.NavigatePairing(it.peer)
                            )
                        },
                        onFailure = { reporter.report(it, "could not reconnect ${key.deviceId}") },
                    )
            }

            DeviceDetailsIntent.ForgetClicked -> viewModelScope.launch { forget() }
        }
    }

    /** Trust first, then the session: a live link would otherwise re-record the key it just lost. */
    private suspend fun forget() {
        trustedDevices.forget(key.deviceId)
        devicesRepository.disconnect(key.deviceId)
            .onFailure {
                reporter.report(it, "forgot ${key.deviceId} but could not close its session")
            }

        effects.send(DeviceDetailsUiEffect.NavigateBack)
    }
}

private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

private fun Instant.formatted(): String = dateFormat.format(
    JavaInstant.ofEpochMilli(toEpochMilliseconds()).atZone(ZoneId.systemDefault())
)
