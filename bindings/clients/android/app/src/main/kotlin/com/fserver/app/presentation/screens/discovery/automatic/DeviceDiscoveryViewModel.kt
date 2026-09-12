package com.fserver.app.presentation.screens.discovery.automatic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.composable.model.firstAction
import com.fserver.app.presentation.composable.model.toRows
import com.fserver.app.presentation.screens.discovery.automatic.model.DeviceDiscoveryIntent
import com.fserver.app.presentation.screens.discovery.automatic.model.DeviceDiscoveryState
import com.fserver.app.presentation.screens.discovery.shared.toCardUi
import com.fserver.core.network.TransportKind
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.device.model.ForeignDevice
import com.fserver.core.network.info.NetworkInfoRepository
import com.fserver.core.requirement.RequirementReport
import com.fserver.core.requirement.RequirementsChecker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Search is the user's to start.
 *
 * The screen offers every method, says what each still needs, and scans only what was ticked
 * when the button was pressed. Nothing here runs on its own — a method that quietly starts
 * itself turns a missing permission into "found nothing", which is the failure this whole flow
 * exists to avoid.
 *
 * Scanning only: whether this device is findable in return is the "discoverable" setting's call,
 * and `AppViewModel` acts on it. A search screen that also put the device on the air would be
 * advertising for as long as it happened to be open, which is not what anyone asked for.
 */
class DeviceDiscoveryViewModel(
    private val networkInfoRepository: NetworkInfoRepository,
    private val devicesRepository: DevicesRepository,
    private val requirementsChecker: RequirementsChecker,
) : ViewModel() {
    private val selected = MutableStateFlow<Set<TransportKind>>(emptySet())
    private val openSetup = MutableStateFlow<TransportKind?>(null)
    private val reports = MutableStateFlow<Map<TransportKind, RequirementReport>>(emptyMap())

    /** What Android still wants before it will name the network. */
    private val networkReport = MutableStateFlow(RequirementReport.Satisfied)

    /**
     * Whether the user has started a search here.
     *
     * Kept separately from "something is scanning right now", which is what it used to be
     * derived from: methods finish at wildly different times, so deriving it meant the results
     * vanished and the screen dropped back to picking methods the moment the last scanner ended.
     */
    private val searchStarted = MutableStateFlow(false)

    /** Methods started at least once in this search — the ones offered a retry when they end. */
    private val startedMethods = MutableStateFlow<Set<TransportKind>>(emptySet())

    /** One job per running method, so a single method can be stopped or restarted on its own. */
    private val scanJobs = mutableMapOf<TransportKind, Job>()

    private val networkCard = combine(
        networkInfoRepository.networkInfo,
        networkReport,
    ) { network, report -> network.toCardUi() to report.firstAction }

    private val participation = combine(selected, startedMethods, ::Pair)

    /**
     * What the search has turned up and not paired with yet: heard over a scan, or handshaken
     * earlier in this process and since let go.
     * Connected devices are counted apart - a method's "found" count is about what it is finding now.
     */
    private val unpaired = combine(
        devicesRepository.devices.discovered,
        devicesRepository.devices.handshaken,
    ) { discovered, handshaken -> discovered + handshaken }

    /** Everything on the list, paired ones first - they carry the marker that says so. */
    private val visible = combine(
        devicesRepository.devices.connected,
        unpaired,
    ) { connected, rest -> connected + rest }

    private val methodsUi = combine(
        unpaired,
        devicesRepository.discovery.runningMethods,
        participation,
        reports,
    ) { devices, running, (selectedMethods, started), reportByMethod ->
        val foundByMethod = devices.groupingBy { it.foundBy }.eachCount()

        searchableTransportKinds.map { method ->
            val report = reportByMethod[method]
            DeviceDiscoveryState.MethodUi(
                method = method,
                selected = method in selectedMethods,
                isScanning = method in running,
                hasRun = method in started,
                foundCount = foundByMethod[method] ?: 0,
                unmetCount = report?.let { it.solvable.size + it.blockers.size },
                isBlocked = report?.blockers?.isNotEmpty() == true,
            )
        }
    }

    private val setupUi = combine(openSetup, reports) { method, reportByMethod ->
        val report = method?.let(reportByMethod::get) ?: return@combine null

        DeviceDiscoveryState.MethodSetupUi(
            method = method,
            solvable = report.solvable.toRows(),
            blockers = report.blockers.toRows(),
        )
    }

    val state: StateFlow<DeviceDiscoveryState> = combine(
        networkCard,
        visible,
        methodsUi,
        setupUi,
        searchStarted,
    ) { (network, networkAction), devices, methods, setup, searching ->
        DeviceDiscoveryState(
            network = network,
            networkAction = networkAction,
            devices = devices.map { it.toUi() },
            methods = methods,
            isSearching = searching,
            methodSetup = setup,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DeviceDiscoveryState(),
        )

    init {
        viewModelScope.launch {
            networkInfoRepository.networkInfo.collect { checkRequirements() }
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
    }

    fun onIntent(intent: DeviceDiscoveryIntent) {
        when (intent) {
            is DeviceDiscoveryIntent.MethodToggled -> toggle(intent.method)

            is DeviceDiscoveryIntent.MethodClicked -> openSetup.value = intent.method

            is DeviceDiscoveryIntent.MethodStartRequested -> {
                selected.update { it + intent.method }
                startSearch(setOf(intent.method))
            }

            DeviceDiscoveryIntent.MethodSetupDismissed -> {
                openSetup.value = null
                checkRequirements()
            }

            DeviceDiscoveryIntent.StartSearchClicked -> {
                searchStarted.value = true
                startSearch(selected.value)
            }

            DeviceDiscoveryIntent.StopSearchClicked -> stopSearch()
        }
    }

    private fun toggle(method: TransportKind) {
        val nowSelected = method !in selected.value
        selected.update { if (nowSelected) it + method else it - method }

        // Ticking a method while a search is already running joins it to that search, rather
        // than making the user stop and start over.
        when {
            !nowSelected -> scanJobs.remove(method)?.cancel()
            scanJobs.isNotEmpty() -> startSearch(setOf(method))
        }
    }

    private fun checkRequirements() {
        viewModelScope.launch {
            val next =
                searchableTransportKinds.associateWith { requirementsChecker.forTransport(it) }
            val wasReady = reports.value.filterValues { it.isSatisfied }.keys
            val isReady = next.filterValues { it.isSatisfied }.keys

            reports.value = next
            networkReport.value = requirementsChecker.forNetworkInfo()

            // Granting the last thing a method was waiting on closes its sheet: there is nothing
            // left on it to read or press.
            openSetup.update { open -> open?.takeUnless { next[it]?.isSatisfied == true } }

            // A method that has just become usable ticks itself - there is no separate "enable".
            // Unticking it survives later re-checks, because only the transition adds it back.
            selected.update { current -> (current + (isReady - wasReady)) intersect isReady }
        }
    }

    private fun startSearch(methods: Set<TransportKind>) {
        methods.forEach { method ->
            if (scanJobs[method]?.isActive == true) return@forEach

            startedMethods.update { it + method }
            scanJobs[method] = viewModelScope.launch {
                try {
                    // TODO: surface the failed Result instead of dropping it
                    devicesRepository.discovery.start(method)
                } finally {
                    scanJobs.remove(method)
                }
            }
        }
    }

    /**
     * Stops the scanners and nothing else — the search screen, and everything already found on
     * it, stays exactly where it is. Each stopped method can be started again on its own.
     *
     * Cancelling the job *is* the stop: `DeviceDiscovery.start` is a suspend function that runs
     * until the scan ends, and it clears the method from `runningMethods` on its way out, so the
     * UI follows. `DeviceDiscovery.stop` is for stopping a scan this screen did not start.
     */
    private fun stopSearch() {
        scanJobs.values.toList().forEach(Job::cancel)
        scanJobs.clear()
    }
}

/**
 * The methods the search screen offers.
 *
 * [TransportKind.ManualAddress] is excluded: a typed address is not something the
 * screen can go and look for, so it is its own entry on the connection screen instead.
 */
private val searchableTransportKinds: List<TransportKind> =
    TransportKind.entries.filterNot { it == TransportKind.ManualAddress }

private fun ForeignDevice.toUi() = DeviceDiscoveryState.DeviceUi(
    id = deviceId,
    name = displayName,
    kind = kind,
    address = routes.first().address,
    isPaired = hasSession,
)
