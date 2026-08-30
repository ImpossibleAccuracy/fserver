package com.fserver.app.presentation.screens.target

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.screens.target.model.TargetDeviceIntent
import com.fserver.app.presentation.screens.target.model.TargetDeviceState
import com.fserver.app.presentation.screens.target.model.TargetDeviceUiEffect
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.device.model.ForeignDevice
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The device a source about to be configured will feed.
 *
 * The list is live, so a device paired through "add a device" appears here on its own when the
 * user comes back, already selected — see [awaitedFrom].
 */
class TargetDeviceViewModel(
    devicesRepository: DevicesRepository,
) : ViewModel() {

    private val effects = Channel<TargetDeviceUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    private val connected = MutableStateFlow<List<ForeignDevice>>(emptyList())
    private val selectedDeviceId = MutableStateFlow<String?>(null)

    /**
     * Devices already connected when the user left to add one, or null when they did not.
     * Anything outside that set arriving afterwards is what they went to connect.
     */
    private val awaitedFrom = MutableStateFlow<Set<String>?>(null)

    val state: StateFlow<TargetDeviceState> = combine(
        connected,
        selectedDeviceId,
    ) { devices, selectedId ->
        TargetDeviceState(
            devices = devices.map { it.toUi() },
            selectedDeviceId = selectedId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TargetDeviceState(),
    )

    init {
        viewModelScope.launch {
            devicesRepository.onlineDevices
                .map { devices -> devices.filter(ForeignDevice::hasSession) }
                .collect(::onConnectedChanged)
        }
    }

    fun onIntent(intent: TargetDeviceIntent) {
        when (intent) {
            is TargetDeviceIntent.DeviceSelected -> selectedDeviceId.value = intent.deviceId

            TargetDeviceIntent.ConnectRouteOpened ->
                awaitedFrom.value = connected.value.mapTo(mutableSetOf()) { it.deviceId }

            TargetDeviceIntent.ContinueClicked -> {
                val id = selectedDeviceId.value ?: return
                viewModelScope.launch { effects.send(TargetDeviceUiEffect.AnswerDevice(id)) }
            }
        }
    }

    private fun onConnectedChanged(devices: List<ForeignDevice>) {
        val awaited = awaitedFrom.value
        connected.value = devices

        if (awaited == null) return
        val fresh = devices.firstOrNull { it.deviceId !in awaited } ?: return

        awaitedFrom.value = null
        selectedDeviceId.value = fresh.deviceId
    }
}

private fun ForeignDevice.toUi() = TargetDeviceState.DeviceUi(
    id = deviceId,
    name = displayName,
    kind = kind,
    address = routes.firstOrNull()?.address,
)
