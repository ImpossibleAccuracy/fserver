package com.fserver.app.presentation.screens.settings.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.composable.model.labelRes
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.settings.details.model.DeviceDetailsIntent
import com.fserver.app.presentation.screens.settings.details.model.DeviceDetailsState
import com.fserver.app.presentation.screens.settings.details.model.DeviceDetailsUiEffect
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.store.TrustedDevicesStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
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
    private val trustedDevicesStore: TrustedDevicesStore,
) : ViewModel() {

    private val effects = Channel<DeviceDetailsUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    private val trustedKeys = trustedDevicesStore.devices
        .map { records -> records.filter { it.deviceId == key.deviceId } }

    val state: StateFlow<DeviceDetailsState> = combine(
        devicesRepository.device(key.deviceId),
        trustedKeys,
    ) { device, trusted ->
        val record = trusted.maxByOrNull { it.lastSeen }

        DeviceDetailsState(
            name = device?.displayName ?: record?.displayName.orEmpty(),
            isConnected = device?.hasSession == true,
            address = device?.routes?.firstOrNull()?.address,
            fingerprintGroups = device?.handshake?.fingerprint?.split(" ").orEmpty(),
            lastSeen = record?.lastSeen?.formatted(),
            methodLabel = record?.method?.labelRes,
            protocolVersion = device?.handshake?.protocolVersion,
            isTrusted = record != null,
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
                    .onFailure { Timber.w(it, "could not disconnect ${key.deviceId}") }
            }

            DeviceDetailsIntent.ForgetClicked -> viewModelScope.launch { forget() }
        }
    }

    /** Trust first, then the session: a live link would otherwise re-record the key it just lost. */
    private suspend fun forget() {
        trustedDevicesStore.findByDeviceId(key.deviceId).forEach { record ->
            trustedDevicesStore.delete(record.publicKey)
        }
        devicesRepository.disconnect(key.deviceId)
            .onFailure { Timber.w(it, "forgot ${key.deviceId} but could not close its session") }

        effects.send(DeviceDetailsUiEffect.NavigateBack)
    }
}

private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

private fun Instant.formatted(): String = dateFormat.format(
    JavaInstant.ofEpochMilli(toEpochMilliseconds()).atZone(ZoneId.systemDefault())
)
