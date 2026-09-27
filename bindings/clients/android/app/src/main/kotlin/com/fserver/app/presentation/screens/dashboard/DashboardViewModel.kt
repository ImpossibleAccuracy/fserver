package com.fserver.app.presentation.screens.dashboard

import android.text.format.DateUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.StorageUsageUi
import com.fserver.app.presentation.composable.model.direction
import com.fserver.app.presentation.composable.model.toUi
import com.fserver.app.presentation.model.UiText
import com.fserver.app.presentation.screens.dashboard.model.DashboardIntent
import com.fserver.app.presentation.screens.dashboard.model.DashboardState
import com.fserver.app.presentation.screens.source.shared.model.latest
import com.fserver.app.presentation.screens.source.shared.model.toUi
import com.fserver.app.presentation.shared.error.ErrorReporter
import com.fserver.app.presentation.shared.error.toAppError
import com.fserver.app.util.combineMany
import com.fserver.core.disk.DiskUsageRepository
import com.fserver.core.network.device.DeviceReachability
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.device.model.DeviceKind
import com.fserver.core.network.device.model.FailedContact
import com.fserver.core.network.device.model.ForeignDevice
import com.fserver.core.network.device.model.ReachabilityFailure
import com.fserver.core.network.device.model.TrustedDevice
import com.fserver.core.network.info.NetworkInfoRepository
import com.fserver.core.network.info.model.NetworkCapability
import com.fserver.core.network.info.model.NetworkInfo
import com.fserver.core.requirement.RequirementReport
import com.fserver.core.requirement.RequirementsChecker
import com.fserver.core.storage.RegisteredSourcesRepository
import com.fserver.core.storage.TrustedDevicesRepository
import com.fserver.core.sync.SourcesController
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.progress.FileTransfer
import com.fserver.core.sync.progress.SourcePass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    private val reporter: ErrorReporter,
) : ViewModel() {
    /**
     * What is still missing before this device could name the network it is on. Kept from the last
     * read rather than re-checked on the tap, so the banner and the sheet it opens describe the
     * same moment.
     */
    private val networkRequirements = MutableStateFlow(RequirementReport.Satisfied)

    private val links: Flow<List<DashboardState.LinkUi>> = combineMany(
        registeredSourcesRepository.sources,
        sourcesController.progress.passes,
        sourcesController.progress.transfers,
        trustedDevicesRepository.devices,
        devicesRepository.devices.connected,
        deviceReachability.failures,
    ) { sources, passes, transfers, trusted, connected, failures ->
        sources
            .sortedBy { it.createdAt }
            .map { source ->
                val session = connected.firstOrNull { it.deviceId == source.deviceId }
                val record = trusted.latest(source.deviceId)
                val pass = passes.firstOrNull { it.sourceId == source.id }
                val failure = failures
                    .firstOrNull { it.deviceId == source.deviceId && it.isWarning }
                    .takeIf { session == null }

                source.toLinkUi(
                    pass = pass,
                    files = transfers.filter { it.belongsTo(pass, source.id) },
                    failure = failure,
                    deviceName = session?.displayName ?: record?.displayName ?: source.deviceId,
                    deviceKind = session?.kind ?: record?.metadata?.kind,
                )
            }
    }

    private val devices: Flow<List<DashboardState.DeviceUi>> = combine(
        trustedDevicesRepository.devices,
        devicesRepository.devices.connected,
        registeredSourcesRepository.sources,
        deviceReachability.failures,
    ) { trusted, connected, sources, failures ->
        deviceList(trusted, connected, sources, failures)
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
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardState(),
    )

    fun onIntent(intent: DashboardIntent) {
        when (intent) {
            DashboardIntent.NetworkWarningClicked -> reporter.report(networkRequirements.value)
        }
    }

    /** Only devices some source is paired with: the rest have nothing to show here. */
    private fun deviceList(
        trusted: List<TrustedDevice>,
        connected: List<ForeignDevice>,
        sources: List<SourceEntry>,
        failures: List<ReachabilityFailure>,
    ): List<DashboardState.DeviceUi> {
        val online = connected.associateBy { it.deviceId }
        // Only the failures worth reporting: the rest read as a device that is simply offline.
        val unreachable = failures.filter { it.isWarning }.mapTo(mutableSetOf()) { it.deviceId }

        return sources
            .map { it.deviceId }
            .distinct()
            .map { deviceId ->
                val record = trusted.latest(deviceId)
                val session = online[deviceId]

                DashboardState.DeviceUi(
                    id = deviceId,
                    name = session?.displayName ?: record?.displayName ?: deviceId,
                    kind = session?.kind ?: record?.metadata?.kind,
                    online = session != null,
                    unreachable = session == null && deviceId in unreachable,
                    lastSeenLabel = record?.metadata?.lastSeen?.relative(),
                )
            }
            .sortedWith(compareByDescending<DashboardState.DeviceUi> { it.online }.thenBy { it.name })
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

private fun FileTransfer.belongsTo(pass: SourcePass?, sourceId: String): Boolean =
    pass?.isFinished == false && key.sourceId == sourceId && startedAt >= pass.startedAt

private fun SourceEntry.toLinkUi(
    pass: SourcePass?,
    files: List<FileTransfer>,
    failure: ReachabilityFailure?,
    deviceName: String,
    deviceKind: DeviceKind?,
): DashboardState.LinkUi {
    val running = pass?.isFinished == false

    return DashboardState.LinkUi(
        id = id,
        label = label,
        deviceName = deviceName,
        deviceKind = deviceKind,
        mode = syncMode.toUi(),
        direction = direction(),
        status = when {
            running -> DashboardState.LinkStatusUi.Syncing
            status is SourceEntry.Status.Pending -> DashboardState.LinkStatusUi.Pending
            status is SourceEntry.Status.Disabled -> DashboardState.LinkStatusUi.Disabled
            else -> DashboardState.LinkStatusUi.Active
        },
        statusDetail = when {
            running -> null
            status is SourceEntry.Status.Disabled -> (status as SourceEntry.Status.Disabled).reason
            else -> lastSyncedAt?.relative()
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
