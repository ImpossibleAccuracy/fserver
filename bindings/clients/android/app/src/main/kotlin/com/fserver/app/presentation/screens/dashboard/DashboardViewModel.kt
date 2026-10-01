package com.fserver.app.presentation.screens.dashboard

import com.fserver.app.util.stateInScreen
import android.text.format.DateUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.PeerUi
import com.fserver.app.presentation.composable.model.StorageUsageUi
import com.fserver.app.presentation.composable.model.peerOf
import com.fserver.app.presentation.composable.model.peers
import com.fserver.app.presentation.composable.model.direction
import com.fserver.app.presentation.composable.model.toUi
import com.fserver.app.presentation.model.UiText
import com.fserver.app.presentation.screens.dashboard.model.DashboardIntent
import com.fserver.app.presentation.screens.dashboard.model.DashboardState
import com.fserver.app.presentation.screens.dashboard.model.DashboardUiEffect
import com.fserver.app.presentation.screens.source.shared.model.transfersOf
import com.fserver.app.presentation.screens.source.shared.model.toUi
import com.fserver.app.presentation.shared.error.ErrorReporter
import com.fserver.app.presentation.shared.error.toAppError
import com.fserver.app.util.combineMany
import com.fserver.core.disk.DiskUsageRepository
import com.fserver.core.network.device.DeviceReachability
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.device.model.FailedContact
import com.fserver.core.network.device.model.ReachabilityFailure
import com.fserver.core.network.info.NetworkInfoRepository
import com.fserver.core.network.info.model.NetworkCapability
import com.fserver.core.network.info.model.NetworkInfo
import com.fserver.core.oneshot.OneShotTransfersController
import com.fserver.core.requirement.RequirementReport
import com.fserver.core.requirement.RequirementsChecker
import com.fserver.core.storage.RegisteredSourcesRepository
import com.fserver.core.storage.TrustedDevicesRepository
import com.fserver.core.sync.SourcesController
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.progress.FileTransfer
import com.fserver.core.sync.progress.SourcePass
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.time.Instant

class DashboardViewModel(
    private val sourcesController: SourcesController,
    private val trustedDevicesRepository: TrustedDevicesRepository,
    private val registeredSourcesRepository: RegisteredSourcesRepository,
    private val devicesRepository: DevicesRepository,
    private val deviceReachability: DeviceReachability,
    private val networkInfoRepository: NetworkInfoRepository,
    private val diskUsage: DiskUsageRepository,
    private val requirementsChecker: RequirementsChecker,
    private val oneShotTransfers: OneShotTransfersController,
    private val reporter: ErrorReporter,
) : ViewModel() {
    private val effects = Channel<DashboardUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    /**
     * What is still missing before this device could name the network it is on. Kept from the last
     * read rather than re-checked on the tap, so the banner and the sheet it opens describe the
     * same moment.
     */
    private val networkRequirements = MutableStateFlow(RequirementReport.Satisfied)

    private val links: Flow<List<DashboardState.LinkUi>> = combine(
        registeredSourcesRepository.sources,
        sourcesController.progress.passes,
        sourcesController.progress.transfers,
        devicesRepository.peers(),
        deviceReachability.failures,
    ) { sources, passes, transfers, peers, failures ->
        sources
            .sortedBy { it.createdAt }
            .map { source ->
                val peer = peers.peerOf(source.deviceId)
                val pass = passes.firstOrNull { it.sourceId == source.id }
                val failure = failures
                    .firstOrNull { it.deviceId == source.deviceId && it.isWarning }
                    .takeUnless { peer.online }

                source.toLinkUi(
                    pass = pass,
                    files = pass?.takeUnless { it.isFinished }.transfersOf(source.id, transfers),
                    failure = failure,
                    peer = peer,
                )
            }
    }

    private val devices: Flow<List<DashboardState.DeviceUi>> = combine(
        devicesRepository.peers(),
        registeredSourcesRepository.sources,
        deviceReachability.failures,
    ) { peers, sources, failures ->
        deviceList(peers, sources, failures)
    }

    private val storage: Flow<StorageUsageUi> = combine(
        diskUsage.usage,
        registeredSourcesRepository.indexedSize,
        registeredSourcesRepository.remoteOnly,
    ) { usage, indexed, remoteOnly ->
        usage.toUi(
            indexedBytes = indexed.bytes,
            remoteOnlyFiles = remoteOnly.count,
            remoteOnlyBytes = remoteOnly.size.bytes,
        )
    }

    private val network: Flow<Pair<DashboardState.NetworkUi?, Boolean>> = combine(
        networkInfoRepository.networkInfo,
        devicesRepository.discovery.runningMethods,
    ) { network, running -> network?.toUi() to running.isNotEmpty() }

    val state: StateFlow<DashboardState> = combineMany(
        storage,
        links,
        network,
        devices,
        sourcesController.incomingRequests,
        networkWarning(),
    ) { storage, links, (network, discovering), devices, requests, warning ->
        DashboardState(
            isLoading = false,
            storage = storage,
            links = links,
            network = network,
            discovering = discovering,
            devices = devices,
            syncRequestsWaiting = requests.size,
            networkWarning = warning,
        )
    }.stateInScreen(viewModelScope, DashboardState())

    fun onIntent(intent: DashboardIntent) {
        when (intent) {
            DashboardIntent.NetworkWarningClicked -> reporter.report(networkRequirements.value)

            is DashboardIntent.SendFiles -> viewModelScope.launch {
                oneShotTransfers.sendShared(intent.deviceId, intent.uris)
                    .onSuccess { effects.send(DashboardUiEffect.FilesOffered(it.files.size)) }
                    .onFailure { reporter.report(it, "could not send files to ${intent.deviceId}") }
            }
        }
    }

    /** Only devices some source is paired with: the rest have nothing to show here. */
    private fun deviceList(
        peers: Map<String, PeerUi>,
        sources: List<SourceEntry>,
        failures: List<ReachabilityFailure>,
    ): List<DashboardState.DeviceUi> {
        // Only the failures worth reporting: the rest read as a device that is simply offline.
        val unreachable = failures.filter { it.isWarning }.mapTo(mutableSetOf()) { it.deviceId }

        return sources
            .map { it.deviceId }
            .distinct()
            .map { deviceId ->
                val peer = peers.peerOf(deviceId)
                DashboardState.DeviceUi(peer = peer, unreachable = !peer.online && deviceId in unreachable)
            }
            .sortedWith(compareByDescending<DashboardState.DeviceUi> { it.peer.online }.thenBy { it.peer.name })
    }

    private fun networkWarning(): Flow<DashboardState.NetworkWarningUi?> = combine(
        networkInfoRepository.networkInfo,
        trustedDevicesRepository.devices,
        devicesRepository.devices.connected,
    ) { network, trusted, connected ->
        val knownNetworks = trusted.mapNotNull { it.metadata?.lastNetworkId }.toSet()

        when {
            network == null -> DashboardState.NetworkWarningUi.NoNetwork

            NetworkCapability.LOCAL_SUBNET !in network.capabilities ->
                DashboardState.NetworkWarningUi.NoLocalNetwork

            connected.isNotEmpty() || knownNetworks.isEmpty() -> null

            // Android answers an ungranted SSID/BSSID read with a redacted placeholder rather than
            // a refusal, so a missing id is the location gate and not a network without one.
            // Calling that "a different network" would be a guess drawn from a permission.
            network.id == null -> unnamedNetworkWarning()

            network.id !in knownNetworks -> DashboardState.NetworkWarningUi.DifferentNetwork

            else -> null
        }
    }

    /**
     * The nameless-network case, once it is known whether anything can be done about it. Nothing
     * is said when the grants are already in place: the network is then simply one Android will
     * not name, and there is no fix to offer.
     */
    private suspend fun unnamedNetworkWarning(): DashboardState.NetworkWarningUi? {
        val report = requirementsChecker.forNetworkInfo()
        networkRequirements.value = report

        return DashboardState.NetworkWarningUi.UnnamedNetwork.takeUnless { report.isSatisfied }
    }
}

private fun SourceEntry.toLinkUi(
    pass: SourcePass?,
    files: List<FileTransfer>,
    failure: ReachabilityFailure?,
    peer: PeerUi,
): DashboardState.LinkUi {
    val running = pass?.isFinished == false

    return DashboardState.LinkUi(
        id = id,
        label = label,
        peer = peer,
        mode = syncMode.toUi(),
        direction = direction(),
        status = when {
            running -> DashboardState.LinkStatusUi.Syncing
            else -> when (status) {
                SourceEntry.Status.Active -> DashboardState.LinkStatusUi.Active
                SourceEntry.Status.Pending -> DashboardState.LinkStatusUi.Pending
                is SourceEntry.Status.Disabled -> DashboardState.LinkStatusUi.Disabled
            }
        },
        statusDetail = when {
            running -> null
            else -> (status as? SourceEntry.Status.Disabled)?.reason ?: lastSyncedAt?.relative()
        },
        progress = (pass as? SourcePass.Local)?.progress,
        filesDone = files.count { it.state == FileTransfer.State.Completed },
        filesTotal = files.size,
        filesSkipped = (pass as? SourcePass.Local)?.filesSkipped ?: 0,
        error = failure?.reason?.toUiText() ?: pass?.takeIf { it.isFailed }?.toAppError()?.message,
    )
}

private val SourcePass.isFailed: Boolean
    get() = when (this) {
        is SourcePass.Local -> stage == SourcePass.Local.Stage.Failed
        is SourcePass.Remote -> stage == SourcePass.Remote.Stage.Failed ||
                stage == SourcePass.Remote.Stage.Abandoned
    }

private fun FailedContact.Reason.toUiText(): UiText = UiText.of(
    when (this) {
        FailedContact.Reason.NoRoute -> R.string.dashboard_link_error_no_route
        FailedContact.Reason.Unreachable -> R.string.dashboard_link_error_unreachable
        FailedContact.Reason.Refused -> R.string.dashboard_link_error_refused
        FailedContact.Reason.NotAllowed -> R.string.dashboard_link_error_not_allowed
        FailedContact.Reason.Failed -> R.string.dashboard_link_error_failed
    }
)

private fun NetworkInfo.toUi() = DashboardState.NetworkUi(
    kind = when (this) {
        is NetworkInfo.WiFi -> DashboardState.NetworkKindUi.WiFi
        is NetworkInfo.Mobile -> DashboardState.NetworkKindUi.Mobile
        NetworkInfo.Wired -> DashboardState.NetworkKindUi.Wired
        NetworkInfo.Other -> DashboardState.NetworkKindUi.Other
    },
    name = name,
)

private fun Instant.relative(): String = DateUtils.getRelativeTimeSpanString(
    toEpochMilliseconds(),
    System.currentTimeMillis(),
    DateUtils.MINUTE_IN_MILLIS,
).toString()
