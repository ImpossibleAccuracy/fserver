package com.fserver.app.presentation.screens.source.setup.target

import com.fserver.app.presentation.screens.source.setup.shared.model.SourceSetupState
import com.fserver.app.presentation.screens.source.setup.target.model.SourceTargetIntent
import com.fserver.app.presentation.screens.source.setup.target.model.SourceTargetState
import com.fserver.app.presentation.screens.source.setup.target.model.SourceTargetUiEffect
import com.fserver.common.utils.chainWith
import com.fserver.core.network.TransportKind
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.device.model.ForeignDevice
import com.fserver.core.network.device.model.TrustedDevice
import com.fserver.core.network.info.model.PeerLocator
import com.fserver.core.storage.TrustedDevicesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class SourceTargetHandler(
    private val devicesRepository: DevicesRepository,
    private val trustedDevicesRepository: TrustedDevicesRepository,
    private val flow: MutableStateFlow<SourceSetupState>,
    private val scope: CoroutineScope,
) {
    private val effectChannel = Channel<SourceTargetUiEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private val editable = MutableStateFlow(Editable())

    private val scanJobs = mutableMapOf<TransportKind, Job>()

    private var awaitedDeviceId: String? = null

    /**
     * The three live feeds this screen draws, folded into one so the [combine] below still fits.
     *
     * `unknown` is what the "discovered" bucket is made of rather than everything visible: a
     * trusted device belongs under "known", which is read out of the trust records instead, so it
     * is listed whether it is around or not. `known` is only there to fill in what a trust record
     * does not store - the device kind.
     */
    private val visible = combine(
        devicesRepository.devices.connected,
        devicesRepository.devices.known,
        devicesRepository.devices.unknown,
        ::Triple,
    )

    val state: StateFlow<SourceTargetState> = combine(
        visible,
        trustedDevicesRepository.devices,
        devicesRepository.discovery.runningMethods,
        flow,
        editable,
    ) { (connected, visibleTrusted, discovered), trusted, running, shared, local ->
        val sessionIds = connected.mapTo(mutableSetOf()) { it.deviceId }

        val known = trusted
            .distinctBy { it.deviceId }
            .filterNot { it.deviceId in sessionIds }

        SourceTargetState(
            connected = connected.map { it.toUi() },
            known = known.map {
                it.toUi(
                    visible = visibleTrusted,
                    isBusy = it.deviceId == local.reconnectingDeviceId
                )
            },
            discovered = discovered.map { it.toUi() },
            selectedDeviceId = local.selectedDeviceId ?: shared.targetDeviceId,
            isSearching = TransportKind.MulticastDns in running,
            isScanningSubnet = TransportKind.SubnetScan in running,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), SourceTargetState())

    init {
        scope.launch {
            devicesRepository.devices.connected.collect(::onConnectedChanged)
        }
    }

    fun onResumed() {
        startDetection(TransportKind.MulticastDns)
    }

    fun onPaused() {
        scanJobs.values.toList().forEach(Job::cancel)
        scanJobs.clear()
    }

    fun onIntent(intent: SourceTargetIntent) {
        when (intent) {
            is SourceTargetIntent.DeviceSelected ->
                editable.update { it.copy(selectedDeviceId = intent.deviceId) }

            is SourceTargetIntent.ReconnectClicked -> reconnect(intent.deviceId)

            SourceTargetIntent.SubnetScanClicked -> startDetection(TransportKind.SubnetScan)

            is SourceTargetIntent.DiscoveredDeviceClicked -> {
                awaitedDeviceId = intent.deviceId
                scope.launch {
                    effectChannel.send(
                        SourceTargetUiEffect.NavigatePairing(
                            PeerLocator.DiscoveredDevice(intent.deviceId)
                        )
                    )
                }
            }

            SourceTargetIntent.Confirmed -> commit()
        }
    }

    fun reset() {
        onPaused()
        awaitedDeviceId = null
        editable.value = Editable()
    }

    private fun commit() {
        val selected = state.value.selectedDeviceId ?: return
        flow.update { it.copy(targetDeviceId = selected) }
    }

    private fun startDetection(method: TransportKind) {
        if (scanJobs[method]?.isActive == true) return

        scanJobs[method] = scope.launch {
            try {
                devicesRepository.discovery.start(method)
                    .onFailure { Timber.w(it, "could not start $method") }
            } finally {
                scanJobs.remove(method)
            }
        }
    }

    private fun reconnect(deviceId: String) {
        if (editable.value.reconnectingDeviceId != null) return

        scope.launch {
            editable.update { it.copy(reconnectingDeviceId = deviceId) }
            try {
                val knownPeer = trustedDevicesRepository.observeKnownRoute(deviceId).firstOrNull()
                    ?.asPeerLocator()

                val result = if (knownPeer == null) {
                    devicesRepository.probe(PeerLocator.DiscoveredDevice(deviceId))
                } else {
                    devicesRepository.probe(knownPeer)
                        .chainWith {
                            // Route might have changed since the last time we saw it, so try probing the discovered route as a fallback
                            devicesRepository.probe(PeerLocator.DiscoveredDevice(deviceId))
                        }
                }

                result.fold(
                    onSuccess = {
                        awaitedDeviceId = deviceId
                        effectChannel.send(SourceTargetUiEffect.NavigatePairing(it.peer))
                    },
                    onFailure = { t ->
                        Timber.w(t, "could not reconnect $deviceId")
                        effectChannel.send(SourceTargetUiEffect.ReconnectFailed(t.localizedMessage))
                    },
                )
            } finally {
                editable.update { it.copy(reconnectingDeviceId = null) }
            }
        }
    }

    private fun onConnectedChanged(devices: List<ForeignDevice>) {
        val awaited = awaitedDeviceId ?: return
        if (devices.none { it.deviceId == awaited }) return

        awaitedDeviceId = null
        editable.update { it.copy(selectedDeviceId = awaited) }
    }

    private data class Editable(
        val selectedDeviceId: String? = null,
        val reconnectingDeviceId: String? = null,
    )
}

private fun ForeignDevice.toUi() = SourceTargetState.DeviceUi(
    id = deviceId,
    name = displayName,
    kind = kind,
    address = routes.firstOrNull()?.address,
)

/** [visible] is the visible-and-trusted feed: what a trust record cannot say, it fills in. */
private fun TrustedDevice.toUi(
    visible: List<ForeignDevice>,
    isBusy: Boolean,
): SourceTargetState.DeviceUi {
    val live = visible.firstOrNull { it.deviceId == deviceId }

    return SourceTargetState.DeviceUi(
        id = deviceId,
        name = displayName,
        kind = live?.kind,
        address = live?.routes?.firstOrNull()?.address,
        isBusy = isBusy,
    )
}
