package com.fserver.app.presentation.screens.discovery.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.composable.model.firstAction
import com.fserver.app.presentation.composable.model.toRows
import com.fserver.app.presentation.screens.discovery.connect.model.ConnectIntent
import com.fserver.app.presentation.screens.discovery.connect.model.ConnectState
import com.fserver.app.presentation.screens.discovery.connect.model.ConnectUiEffect
import com.fserver.app.presentation.screens.discovery.shared.toCardUi
import com.fserver.core.network.TransportKind
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.device.model.ForeignDevice
import com.fserver.core.network.device.model.TrustedDevice
import com.fserver.core.network.info.NetworkInfoRepository
import com.fserver.core.network.info.model.PeerLocator
import com.fserver.core.requirement.RequirementReport
import com.fserver.core.requirement.RequirementsChecker
import com.fserver.core.storage.TrustedDevicesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The one place a device is picked: the devices already met, whatever a scan turns up, and the
 * three ways of reaching one that a scan cannot.
 *
 * Only multicast DNS runs on its own, on every resume: it costs no permission and no battery worth
 * the name, so a list that fills itself is worth more than a button. Everything else is behind the
 * methods sheet, because a method that quietly starts without the permission it needs turns a
 * missing grant into "found nothing", which is the failure this screen exists to avoid.
 *
 * Scanning only: whether this device is findable in return is the "discoverable" setting's call,
 * and `AppViewModel` acts on it.
 */
class ConnectViewModel(
    private val networkInfoRepository: NetworkInfoRepository,
    private val devicesRepository: DevicesRepository,
    private val trustedDevicesRepository: TrustedDevicesRepository,
    private val requirementsChecker: RequirementsChecker,
) : ViewModel() {
    private val effectChannel = Channel<ConnectUiEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private val openSetup = MutableStateFlow<TransportKind?>(null)
    private val methodsOpen = MutableStateFlow(false)
    private val reports = MutableStateFlow<Map<TransportKind, RequirementReport>>(emptyMap())
    private val reconnecting = MutableStateFlow<String?>(null)

    /** What Android still wants before it will name the network. */
    private val networkReport = MutableStateFlow(RequirementReport.Satisfied)

    /** Methods started at least once while this screen was open — the ones offered a retry. */
    private val startedMethods = MutableStateFlow<Set<TransportKind>>(emptySet())

    /** One job per running method, so a single method can be stopped or restarted on its own. */
    private val scanJobs = mutableMapOf<TransportKind, Job>()

    /**
     * The device a handshake was started for, held until it shows up connected.
     *
     * Pairing happens on another screen and comes back here; without this the user would return
     * to the list and have to pick the device they just paired with a second time.
     */
    private var awaitedDeviceId: String? = null

    private val networkCard = combine(
        networkInfoRepository.networkInfo,
        networkReport,
    ) { network, report -> network.toCardUi() to report.firstAction }

    /**
     * Connected, then trusted-but-offline, in one list.
     *
     * The offline half is read out of the trust records rather than off the wire, so a device is
     * listed whether it is around or not; the visible-and-trusted feed only fills in what a trust
     * record does not store — the kind and the current address.
     */
    private val knownUi = combine(
        devicesRepository.devices.connected,
        devicesRepository.devices.known,
        trustedDevicesRepository.devices,
        reconnecting,
    ) { connected, visibleTrusted, trusted, busyId ->
        val sessionIds = connected.mapTo(mutableSetOf()) { it.deviceId }

        connected.map { it.toUi(isConnected = true) } +
                trusted
                    .distinctBy { it.deviceId }
                    .filterNot { it.deviceId in sessionIds }
                    .map { it.toUi(visible = visibleTrusted, isBusy = it.deviceId == busyId) }
    }

    private val methodsUi = combine(
        devicesRepository.devices.unknown,
        devicesRepository.discovery.runningMethods,
        startedMethods,
        reports,
    ) { devices, running, started, reportByMethod ->
        val foundByMethod = devices.groupingBy { it.foundBy }.eachCount()

        searchableTransportKinds.map { method ->
            val report = reportByMethod[method]
            ConnectState.MethodUi(
                method = method,
                isScanning = method in running,
                hasRun = method in started,
                foundCount = foundByMethod[method] ?: 0,
                unmetCount = report?.let { it.solvable.size + it.blockers.size },
                isBlocked = report?.blockers?.isNotEmpty() == true,
            )
        }
    }

    private val sheets = combine(methodsOpen, openSetup, reports) { open, method, reportByMethod ->
        val report = method?.let(reportByMethod::get)

        open to report?.let {
            ConnectState.MethodSetupUi(
                method = method,
                solvable = it.solvable.toRows(),
                blockers = it.blockers.toRows(),
            )
        }
    }

    val state: StateFlow<ConnectState> = combine(
        networkCard,
        knownUi,
        devicesRepository.devices.unknown,
        methodsUi,
        sheets,
    ) { (network, networkAction), known, discovered, methods, (methodsOpen, setup) ->
        ConnectState(
            network = network,
            networkAction = networkAction,
            known = known,
            discovered = discovered.map { it.toUi() },
            methods = methods,
            isMethodsOpen = methodsOpen,
            methodSetup = setup,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ConnectState(),
    )

    init {
        viewModelScope.launch {
            networkInfoRepository.networkInfo.collect { checkRequirements() }
        }

        viewModelScope.launch {
            devicesRepository.devices.connected.collect(::onConnectedChanged)
        }
    }

    /**
     * A [RequirementReport] is a snapshot, and the user can change any of it from outside the
     * app, so it is re-read whenever the screen comes back to the foreground.
     */
    fun onResumed() {
        // Granting location changes nothing the platform reports on its own, so the network name
        // stays redacted until it is read again.
        networkInfoRepository.refresh()
        checkRequirements()
        startDetection(TransportKind.MulticastDns)
    }

    fun onPaused() {
        stopSearch()
    }

    fun onIntent(intent: ConnectIntent) {
        when (intent) {
            is ConnectIntent.KnownDeviceClicked -> pick(intent.deviceId)

            is ConnectIntent.DiscoveredDeviceClicked -> {
                awaitedDeviceId = intent.deviceId
                send(ConnectUiEffect.NavigatePairing(PeerLocator.DiscoveredDevice(intent.deviceId)))
            }

            ConnectIntent.MethodsClicked -> methodsOpen.value = true

            ConnectIntent.MethodsDismissed -> methodsOpen.value = false

            is ConnectIntent.MethodToggled -> toggle(intent.method)

            is ConnectIntent.MethodClicked -> openSetup.value = intent.method

            ConnectIntent.MethodSetupDismissed -> {
                openSetup.value = null
                checkRequirements()
            }
        }
    }

    /**
     * A connected device is the answer as it stands; anything else has to be dialled first, and
     * the pairing screen sends the user back here once it is.
     */
    private fun pick(deviceId: String) {
        val device = state.value.known.firstOrNull { it.id == deviceId } ?: return

        if (device.isConnected) {
            send(ConnectUiEffect.DeviceSelected(deviceId))
        } else {
            reconnect(deviceId)
        }
    }

    private fun reconnect(deviceId: String) {
        if (reconnecting.value != null) return

        viewModelScope.launch {
            reconnecting.value = deviceId

            try {
                devicesRepository.probe(PeerLocator.KnownDevice(deviceId))
                    .fold(
                        onSuccess = {
                            awaitedDeviceId = deviceId
                            effectChannel.send(ConnectUiEffect.NavigatePairing(it.peer))
                        },
                        onFailure = { t ->
                            Timber.w(t, "could not reconnect $deviceId")
                            effectChannel.send(
                                ConnectUiEffect.ReconnectFailed(t.localizedMessage)
                            )
                        },
                    )
            } finally {
                // The row stays busy forever if this is missed - reconnect() refuses to run again.
                reconnecting.value = null
            }
        }
    }

    private fun onConnectedChanged(devices: List<ForeignDevice>) {
        val awaited = awaitedDeviceId ?: return
        if (devices.none { it.deviceId == awaited }) return

        awaitedDeviceId = null
        send(ConnectUiEffect.DeviceSelected(awaited))
    }

    /**
     * The row is the method's switch. Methods run independently, so stopping one leaves the rest
     * scanning and what it already found on the list.
     */
    private fun toggle(method: TransportKind) {
        val running = scanJobs.remove(method)
        if (running != null) running.cancel() else startDetection(method)
    }

    private fun checkRequirements() {
        viewModelScope.launch {
            val next =
                searchableTransportKinds.associateWith { requirementsChecker.forTransport(it) }

            reports.value = next
            networkReport.value = requirementsChecker.forNetworkInfo()

            // Granting the last thing a method was waiting on closes its sheet and starts it:
            // there is nothing left on the sheet to read, and the permission was granted to run
            // the method, so asking for a tap on top of it asks the same question twice.
            val open = openSetup.value
            if (open != null && next[open]?.isSatisfied == true) {
                openSetup.value = null
                startDetection(open)
            }
        }
    }

    private fun startDetection(method: TransportKind) {
        if (scanJobs[method]?.isActive == true) return

        startedMethods.update { it + method }
        scanJobs[method] = viewModelScope.launch {
            try {
                devicesRepository.discovery.start(method)
                    .onFailure { Timber.w(it, "could not start $method") }
            } finally {
                scanJobs.remove(method)
            }
        }
    }

    /**
     * Stops the scanners and nothing else — the list, and everything already on it, stays exactly
     * where it is. Each stopped method can be started again on its own.
     *
     * Cancelling the job *is* the stop: `DeviceDiscovery.start` is a suspend function that runs
     * until the scan ends, and it clears the method from `runningMethods` on its way out, so the
     * UI follows. `DeviceDiscovery.stop` is for stopping a scan this screen did not start.
     */
    private fun stopSearch() {
        scanJobs.values.toList().forEach(Job::cancel)
        scanJobs.clear()
    }

    private fun send(effect: ConnectUiEffect) {
        viewModelScope.launch { effectChannel.send(effect) }
    }
}

/**
 * The methods the sheet offers.
 *
 * [TransportKind.ManualAddress] is excluded: a typed address is not something the screen can go
 * and look for, so it is its own button on the screen instead.
 */
private val searchableTransportKinds: List<TransportKind> =
    TransportKind.entries.filterNot { it == TransportKind.ManualAddress }

private fun ForeignDevice.toUi(isConnected: Boolean = false) = ConnectState.DeviceUi(
    id = deviceId,
    name = displayName,
    kind = kind,
    address = routes.firstOrNull()?.address,
    isConnected = isConnected,
)

/** [visible] is the visible-and-trusted feed: what a trust record cannot say, it fills in. */
private fun TrustedDevice.toUi(
    visible: List<ForeignDevice>,
    isBusy: Boolean,
): ConnectState.DeviceUi {
    val live = visible.firstOrNull { it.deviceId == deviceId }

    return ConnectState.DeviceUi(
        id = deviceId,
        name = displayName,
        kind = live?.kind,
        address = live?.routes?.firstOrNull()?.address,
        isBusy = isBusy,
    )
}
