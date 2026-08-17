package com.fserver.app.presentation.screens.files.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.data.SendSelectionStore
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.files.send.model.SendTargetIntent
import com.fserver.app.presentation.screens.files.send.model.SendTargetState
import com.fserver.app.presentation.screens.files.send.model.SendTargetUiEffect
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
 * The device half of the send flow: which connected device gets the selection the picker made.
 *
 * The list is live, so a device paired through one of the connect routes appears here on its own
 * when the user comes back. That return is also when the confirmation is raised — see
 * [awaitedFrom].
 */
class SendTargetViewModel(
    private val key: Destination.Files.SendTarget,
    private val selectionStore: SendSelectionStore,
    devicesRepository: DevicesRepository,
) : ViewModel() {
    private val selection = selectionStore.selection(key.selectionId)

    private val effects = Channel<SendTargetUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    private val connected = MutableStateFlow<List<ForeignDevice>>(emptyList())
    private val confirmingDeviceId = MutableStateFlow<String?>(null)

    /**
     * Devices already connected when the user left for a connect route, or null when they did
     * not. Anything outside that set arriving afterwards is what they went to connect, so it
     * takes the confirmation without a second tap.
     */
    private val awaitedFrom = MutableStateFlow<Set<String>?>(null)

    val state: StateFlow<SendTargetState> = combine(
        connected,
        confirmingDeviceId,
    ) { devices, confirmingId ->
        val deviceUi = devices.map { it.toUi() }
        SendTargetState(
            fileCount = selection?.size ?: 0,
            fileNames = selection?.map { it.name }.orEmpty(),
            devices = deviceUi,
            confirmation = deviceUi.firstOrNull { it.id == confirmingId }?.let {
                SendTargetState.ConfirmationUi(
                    deviceId = it.id,
                    deviceName = it.name,
                    fileCount = selection?.size ?: 0,
                )
            },
            isSelectionLost = selection == null,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SendTargetState(isSelectionLost = selection == null),
    )

    init {
        viewModelScope.launch {
            devicesRepository.onlineDevices
                .map { devices -> devices.filter(ForeignDevice::hasSession) }
                .collect(::onConnectedChanged)
        }
    }

    fun onIntent(intent: SendTargetIntent) {
        when (intent) {
            is SendTargetIntent.DeviceSelected -> confirmingDeviceId.value = intent.deviceId

            SendTargetIntent.ConnectRouteOpened ->
                awaitedFrom.value = connected.value.mapTo(mutableSetOf()) { it.deviceId }

            // Saying no also ends the wait: the device that just connected was the answer,
            // whatever the user decided about it.
            SendTargetIntent.SendCancelled -> {
                confirmingDeviceId.value = null
                awaitedFrom.value = null
            }

            // TODO: hand the selection and the target to :core once transfers exist.
            SendTargetIntent.SendConfirmed -> {
                confirmingDeviceId.value = null
                selectionStore.clear(key.selectionId)
                viewModelScope.launch { effects.send(SendTargetUiEffect.SendStarted) }
            }
        }
    }

    private fun onConnectedChanged(devices: List<ForeignDevice>) {
        val awaited = awaitedFrom.value
        connected.value = devices

        if (awaited == null) return
        val fresh = devices.firstOrNull { it.deviceId !in awaited } ?: return

        awaitedFrom.value = null
        confirmingDeviceId.value = fresh.deviceId
    }
}

private fun ForeignDevice.toUi() = SendTargetState.DeviceUi(
    id = deviceId,
    name = displayName,
    kind = kind,
    address = routes.firstOrNull()?.address,
)
