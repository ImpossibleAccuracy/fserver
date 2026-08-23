package com.fserver.app.presentation.screens.target

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.data.SendSelectionStore
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.model.TargetPurpose
import com.fserver.app.presentation.screens.target.model.TargetDeviceIntent
import com.fserver.app.presentation.screens.target.model.TargetDeviceState
import com.fserver.app.presentation.screens.target.model.TargetDeviceUiEffect
import com.fserver.core.files.transfer.TransferRepository
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.device.model.ForeignDevice
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Which connected device receives the bytes — the picker's selection, or everything a source
 * about to be configured will produce.
 *
 * The list is live, so a device paired through "add a device" appears here on its own when the
 * user comes back, already selected — see [awaitedFrom]. Only [TargetPurpose.SendFiles] does
 * anything irreversible here; configuring a source hands the answer on to the conditions screen.
 */
class TargetDeviceViewModel(
    key: Destination.TargetDevice,
    private val selectionStore: SendSelectionStore,
    private val transferRepository: TransferRepository,
    devicesRepository: DevicesRepository,
) : ViewModel() {
    private val purpose = key.purpose
    private val selectionId = (purpose as? TargetPurpose.SendFiles)?.selectionId
    private val selection = selectionId?.let(selectionStore::selection)

    private val effects = Channel<TargetDeviceUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    private val connected = MutableStateFlow<List<ForeignDevice>>(emptyList())
    private val selectedDeviceId = MutableStateFlow<String?>(null)
    private val confirmingDeviceId = MutableStateFlow<String?>(null)

    /**
     * Devices already connected when the user left to add one, or null when they did not.
     * Anything outside that set arriving afterwards is what they went to connect.
     */
    private val awaitedFrom = MutableStateFlow<Set<String>?>(null)

    /** Configuring a source has no selection behind it, so it can never lose one. */
    private val isSelectionLost = selectionId != null && selection == null

    val state: StateFlow<TargetDeviceState> = combine(
        connected,
        selectedDeviceId,
        confirmingDeviceId,
    ) { devices, selectedId, confirmingId ->
        val deviceUi = devices.map { it.toUi() }
        TargetDeviceState(
            purpose = purpose,
            fileCount = selection?.size ?: 0,
            fileNames = selection?.map { it.name }.orEmpty(),
            devices = deviceUi,
            selectedDeviceId = selectedId,
            confirmation = deviceUi.firstOrNull { it.id == confirmingId }?.let {
                TargetDeviceState.ConfirmationUi(
                    deviceId = it.id,
                    deviceName = it.name,
                    fileCount = selection?.size ?: 0,
                )
            },
            isSelectionLost = isSelectionLost,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TargetDeviceState(purpose = purpose, isSelectionLost = isSelectionLost),
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

            TargetDeviceIntent.ContinueClicked -> onContinue()

            // Saying no also ends the wait: the device that just connected was the answer,
            // whatever the user decided about it.
            TargetDeviceIntent.SendCancelled -> {
                confirmingDeviceId.value = null
                awaitedFrom.value = null
            }

            TargetDeviceIntent.SendConfirmed -> {
                val deviceId = confirmingDeviceId.getAndUpdate { null } ?: return
                viewModelScope.launch { send(deviceId) }
            }
        }
    }

    private fun onContinue() {
        val deviceId = selectedDeviceId.value ?: return
        when (purpose) {
            is TargetPurpose.SendFiles -> confirmingDeviceId.value = deviceId

            // TODO: the chosen device is dropped on the floor. Once a source is something
            // `:core` stores, it travels with the rest of the answers to the conditions screen.
            is TargetPurpose.ConfigureSource ->
                viewModelScope.launch { effects.send(TargetDeviceUiEffect.NavigateToConditions) }
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

    private suspend fun send(deviceId: String) {
        val files = selectionId?.let(selectionStore::selection)

        transferRepository
            .sendFiles(
                deviceId = deviceId,
                filesCount = files?.size ?: Int.MAX_VALUE,
            )
            .fold(
                onSuccess = { isAcceptedByDevice ->
                    if (isAcceptedByDevice) {
                        selectionId?.let(selectionStore::clear)
                        effects.send(
                            TargetDeviceUiEffect.ShowMessage(
                                "Transfer completed!"
                            )
                        )
                        effects.send(TargetDeviceUiEffect.NavigateFinished)
                    } else {
                        effects.send(
                            TargetDeviceUiEffect.ShowMessage("The device rejected the transfer.")
                        )
                    }
                },
                onFailure = {
                    effects.send(
                        TargetDeviceUiEffect.ShowMessage(
                            "Failed to send files: ${it.localizedMessage ?: it::class.simpleName}"
                        )
                    )
                }
            )
    }
}

private fun ForeignDevice.toUi() = TargetDeviceState.DeviceUi(
    id = deviceId,
    name = displayName,
    kind = kind,
    address = routes.firstOrNull()?.address,
)
