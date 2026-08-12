package com.fserver.app.presentation.screens.discovery.automatic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.model.address
import com.fserver.app.presentation.model.foundBy
import com.fserver.app.presentation.model.searchableDetectionMethods
import com.fserver.app.presentation.model.toCardUi
import com.fserver.app.presentation.model.toRows
import com.fserver.app.presentation.screens.discovery.automatic.model.DeviceDiscoveryIntent
import com.fserver.app.presentation.screens.discovery.automatic.model.DeviceDiscoveryState
import com.fserver.core.domain.model.DetectionMethod
import com.fserver.core.domain.model.requirement.RequirementReport
import com.fserver.core.domain.repository.DeviceDetectionRepository
import com.fserver.core.domain.repository.NetworkInfoRepository
import com.fserver.core.domain.repository.RequirementsChecker
import com.fserver.net.discovery.DiscoveredPeer
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
 */
class DeviceDiscoveryViewModel(
    networkInfoRepository: NetworkInfoRepository,
    private val deviceDetectionRepository: DeviceDetectionRepository,
    private val requirementsChecker: RequirementsChecker,
) : ViewModel() {

    private val selected = MutableStateFlow<Set<DetectionMethod>>(emptySet())
    private val openSetup = MutableStateFlow<DetectionMethod?>(null)
    private val reports = MutableStateFlow<Map<DetectionMethod, RequirementReport>>(emptyMap())
    private val networkNamed = MutableStateFlow(false)

    /**
     * Whether the user has started a search here.
     *
     * Kept separately from "something is scanning right now", which is what it used to be
     * derived from: methods finish at wildly different times, so deriving it meant the results
     * vanished and the screen dropped back to picking methods the moment the last scanner ended.
     */
    private val searchStarted = MutableStateFlow(false)

    /** Methods started at least once in this search — the ones offered a retry when they end. */
    private val startedMethods = MutableStateFlow<Set<DetectionMethod>>(emptySet())

    /** One job per running method, so a single method can be stopped or restarted on its own. */
    private val scanJobs = mutableMapOf<DetectionMethod, Job>()

    private val networkCard = combine(
        networkInfoRepository.networkInfo,
        networkNamed,
    ) { network, named -> network.toCardUi(named) }

    private val participation = combine(selected, startedMethods, ::Pair)

    private val methodsUi = combine(
        deviceDetectionRepository.onlineDevices,
        deviceDetectionRepository.runningScanningMethods,
        participation,
        reports,
    ) { devices, running, (selectedMethods, started), reportByMethod ->
        val foundByMethod = devices.groupingBy { it.foundBy }.eachCount()

        searchableDetectionMethods.map { method ->
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
        deviceDetectionRepository.onlineDevices, // TODO: add already connected devices to list
        methodsUi,
        setupUi,
        searchStarted,
    ) { network, devices, methods, setup, searching ->
        DeviceDiscoveryState(
            network = network,
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

    private fun toggle(method: DetectionMethod) {
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
                searchableDetectionMethods.associateWith { requirementsChecker.forDetection(it) }
            val wasReady = reports.value.filterValues { it.isSatisfied }.keys
            val isReady = next.filterValues { it.isSatisfied }.keys

            reports.value = next
            networkNamed.value = requirementsChecker.forNetworkInfo().isSatisfied

            // Granting the last thing a method was waiting on closes its sheet: there is nothing
            // left on it to read or press.
            openSetup.update { open -> open?.takeUnless { next[it]?.isSatisfied == true } }

            // A method that has just become usable ticks itself - there is no separate "enable".
            // Unticking it survives later re-checks, because only the transition adds it back.
            selected.update { current -> (current + (isReady - wasReady)) intersect isReady }
        }
    }

    private fun startSearch(methods: Set<DetectionMethod>) {
        methods.forEach { method ->
            if (scanJobs[method]?.isActive == true) return@forEach

            startedMethods.update { it + method }
            scanJobs[method] = viewModelScope.launch {
                try {
                    // TODO: surface the failed Result instead of dropping it
                    deviceDetectionRepository.startDetection(method)
                } finally {
                    scanJobs.remove(method)
                }
            }
        }

        viewModelScope.launch {
            deviceDetectionRepository.startAdvertising()
        }
    }

    /**
     * Stops the scanners and nothing else — the search screen, and everything already found on
     * it, stays exactly where it is. Each stopped method can be started again on its own.
     *
     * `DeviceDetectionRepository` has no stop of its own: `startDetection` is a suspend function
     * that runs until the scan ends, so cancelling its coroutine *is* the stop. The repository
     * clears the method from `runningScanningMethods` in a `finally`, so the UI follows.
     */
    private fun stopSearch() {
        scanJobs.values.toList().forEach(Job::cancel)
        scanJobs.clear()
    }
}

private fun DiscoveredPeer.toUi() = DeviceDiscoveryState.DeviceUi(
    id = deviceId,
    name = displayName,
    kind = kind,
    address = address,
)
