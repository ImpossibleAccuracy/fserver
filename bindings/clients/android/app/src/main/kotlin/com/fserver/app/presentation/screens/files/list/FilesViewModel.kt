package com.fserver.app.presentation.screens.files.list

import android.text.format.DateUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.screens.files.list.model.FilesIntent
import com.fserver.app.presentation.screens.files.list.model.FilesState
import com.fserver.app.presentation.screens.files.list.model.FilesUiEffect
import com.fserver.app.presentation.screens.files.shared.FilesProviderHandler
import com.fserver.app.presentation.screens.source.request.shared.model.SyncRequestUi
import com.fserver.app.presentation.screens.source.request.shared.model.toUi
import com.fserver.app.presentation.screens.source.shared.model.latest
import com.fserver.app.presentation.screens.source.shared.model.toUi
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.shared.browser.model.asPreviewFile
import com.fserver.app.presentation.shared.error.ErrorReporter
import com.fserver.app.util.combineMany
import com.fserver.core.files.FilesController
import com.fserver.core.files.SyncFileEntry
import com.fserver.core.network.device.DeviceReachability
import com.fserver.core.network.device.DevicesRepository
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
import com.fserver.core.sync.progress.SourcePass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class FilesViewModel(
    private val sourcesController: SourcesController,
    private val trustedDevicesRepository: TrustedDevicesRepository,
    private val registeredSourcesRepository: RegisteredSourcesRepository,
    private val devicesRepository: DevicesRepository,
    private val deviceReachability: DeviceReachability,
    private val networkInfoRepository: NetworkInfoRepository,
    private val filesController: FilesController,
    private val requirementsChecker: RequirementsChecker,
    private val reporter: ErrorReporter,
) : ViewModel() {
    private val effects = Channel<FilesUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    private val filesProviderHandler = FilesProviderHandler(
        filesController = filesController,
        registeredSourcesRepository = registeredSourcesRepository,
        openFile = {
            viewModelScope.launch {
                effects.send(FilesUiEffect.OpenFile(it.asPreviewFile()))
            }
        }
    )

    private val editable = MutableStateFlow(Editable())
    private val refreshing = MutableStateFlow(false)

    /**
     * What is still missing before this device could name the network it is on. Kept from the last
     * read rather than re-checked on the tap, so the banner and the sheet it opens describe the
     * same moment.
     */
    private val networkRequirements = MutableStateFlow(RequirementReport.Satisfied)

    private val entries: StateFlow<FilesState.FeedUi?> = combine(
        editable.map { it.filter to it.selectedDeviceId }.distinctUntilChanged(),
        registeredSourcesRepository.sources,
    ) { (filter, deviceId), sources ->
        Query(
            filter = filter,
            deviceId = deviceId,
            sourceIds = deviceId?.let { id ->
                sources.filter { it.deviceId == id }.map { it.id }.toSet()
            },
        )
    }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            filesProviderHandler
                .loadPreviewFiles(
                    requiredLocation = when (query.filter) {
                        FilesState.FilterUi.All -> null
                        FilesState.FilterUi.Local -> FileBrowserUi.File.Location.Local
                        FilesState.FilterUi.Cloud -> FileBrowserUi.File.Location.Remote
                    },
                    sourceIds = query.sourceIds,
                )
                .map { files ->
                    FilesState.FeedUi(
                        preview = FileBrowserUi.PlainList(files),
                        filter = query.filter,
                        deviceId = query.deviceId,
                    )
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = null,
        )

    private val devices: Flow<List<FilesState.DeviceUi>> = combine(
        trustedDevicesRepository.devices,
        devicesRepository.devices.connected,
        registeredSourcesRepository.sources,
        filesController.overallContent,
        deviceReachability.failures,
    ) { trusted, connected, sources, content, failures ->
        deviceList(trusted, connected, sources, content, failures)
    }

    private val expandedDevice: Flow<FilesState.DeviceDetailsUi?> = editable
        .map { it.expandedDeviceId }
        .distinctUntilChanged()
        .flatMapLatest { deviceId ->
            if (deviceId == null) flowOf(null) else deviceDetails(deviceId)
        }

    private val chrome: Flow<Chrome> = combine(
        sourcesController.incomingRequests,
        trustedDevicesRepository.devices,
        sourcesController.progress.passes,
        refreshing,
        networkWarning(),
    ) { requests, trusted, passes, isRefreshing, warning ->
        Chrome(
            syncRequest = requests.maxByOrNull { it.receivedAt }?.toUi(trusted),
            syncRequestsWaiting = requests.size,
            isSyncing = isRefreshing || passes.any { !it.isFinished },
            networkWarning = warning,
        )
    }

    val state: StateFlow<FilesState> = combine(
        editable,
        entries,
        devices,
        expandedDevice,
        chrome,
    ) { edit, files, devices, expanded, chrome ->
        FilesState(
            devices = devices,
            selectedDeviceId = edit.selectedDeviceId.takeIf { id -> devices.any { it.id == id } },
            filter = edit.filter,
            entries = files,
            expandedDevice = expanded,
            syncRequest = chrome.syncRequest,
            syncRequestsWaiting = chrome.syncRequestsWaiting,
            syncRequestHintDismissed = edit.syncRequestHintDismissed,
            isSyncing = chrome.isSyncing,
            networkWarning = chrome.networkWarning,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FilesState(),
    )

    fun onIntent(intent: FilesIntent) {
        when (intent) {
            is FilesIntent.DeviceClicked -> editable.update {
                it.copy(selectedDeviceId = intent.deviceId.takeIf { id -> id != it.selectedDeviceId })
            }

            is FilesIntent.DeviceExpanded -> editable.update {
                it.copy(expandedDeviceId = intent.deviceId)
            }

            FilesIntent.DeviceCollapsed -> editable.update { it.copy(expandedDeviceId = null) }

            is FilesIntent.FilterSelected -> editable.update { it.copy(filter = intent.filter) }

            FilesIntent.SyncRequestHintDismissed -> editable.update {
                it.copy(syncRequestHintDismissed = true)
            }

            FilesIntent.RefreshRequested -> runSync()

            FilesIntent.NetworkWarningClicked -> reporter.report(networkRequirements.value)

            is FilesIntent.EntryClicked -> openEntry(intent.entryId)
        }
    }

    private fun openEntry(entryId: String) {
        viewModelScope.launch {
            val file = filesController.overallContent.value
                .find { it.fileId == entryId }
                ?: return@launch

            filesProviderHandler.onItemClick(file)
        }
    }

    private fun runSync() {
        if (refreshing.value) return
        refreshing.value = true

        viewModelScope.launch {
            runCatching { sourcesController.runSync() }
                .exceptionOrNull()
                ?.let { reporter.report(it, "Sync from the files screen failed") }

            refreshing.value = false
        }
    }

    private fun deviceList(
        trusted: List<TrustedDevice>,
        connected: List<ForeignDevice>,
        sources: List<SourceEntry>,
        content: List<SyncFileEntry>,
        failures: List<ReachabilityFailure>,
    ): List<FilesState.DeviceUi> {
        val online = connected.associateBy { it.deviceId }
        val itemCounts = itemCountsByDevice(sources, content)
        // Only the failures worth reporting: the rest read as a device that is simply offline.
        val unreachable = failures.filter { it.isWarning }.mapTo(mutableSetOf()) { it.deviceId }

        return (trusted.map { it.deviceId } + online.keys)
            .filter { id ->
                sources.any { it.deviceId == id }
            }
            .distinct()
            .map { deviceId ->
                val record = trusted.latest(deviceId)
                val session = online[deviceId]

                FilesState.DeviceUi(
                    id = deviceId,
                    name = session?.displayName ?: record?.displayName ?: deviceId,
                    kind = session?.kind ?: record?.metadata?.kind,
                    online = session != null,
                    itemCount = itemCounts[deviceId] ?: 0,
                    unreachable = session == null && deviceId in unreachable,
                )
            }
            .sortedWith(compareByDescending<FilesState.DeviceUi> { it.online }.thenBy { it.name })
    }

    private fun itemCountsByDevice(
        sources: List<SourceEntry>,
        content: List<SyncFileEntry>,
    ): Map<String, Int> {
        val deviceOfSource = sources.associate { it.id to it.deviceId }

        return content
            .mapNotNull { deviceOfSource[it.sourceId] }
            .groupingBy { it }
            .eachCount()
    }

    private fun deviceDetails(deviceId: String): Flow<FilesState.DeviceDetailsUi> = combineMany(
        trustedDevicesRepository.devices,
        devicesRepository.devices.device(deviceId),
        trustedDevicesRepository.observeKnownRoute(deviceId),
        registeredSourcesRepository.sources,
        sourcesController.progress.passes,
        filesController.overallContent,
        reachability(deviceId),
    ) { trusted, device, knownRoute, sources, passes, content, reach ->
        val record = trusted.latest(deviceId)
        val bySource = content.groupBy { it.sourceId }

        FilesState.DeviceDetailsUi(
            id = deviceId,
            name = device?.displayName ?: record?.displayName ?: deviceId,
            kind = device?.kind ?: record?.metadata?.kind,
            online = device?.hasSession == true,
            addressLabel = device?.routes?.firstOrNull()?.address ?: knownRoute?.address,
            fingerprintLabel = device?.handshake?.fingerprint ?: record?.fingerprint?.value,
            foundBy = device?.foundBy ?: knownRoute?.transport,
            lastSeenLabel = record?.metadata?.lastSeen?.relative(),
            unreachable = reach
                ?.takeIf { device?.hasSession != true && it.failure.isWarning }
                ?.toUi(lastNetworkId = record?.metadata?.lastNetworkId),
            folders = sources
                .filter { it.deviceId == deviceId }
                .map { source ->
                    source.toFolderUi(
                        pass = passes.firstOrNull { it.sourceId == source.id },
                        entries = bySource[source.id].orEmpty(),
                    )
                },
        )
    }

    private fun reachability(deviceId: String): Flow<Reach?> = combine(
        deviceReachability.device(deviceId),
        networkInfoRepository.networkInfo,
    ) { failure, network ->
        failure?.let { Reach(failure = it, network = network) }
    }

    private fun networkWarning(): Flow<FilesState.NetworkWarningUi?> = combine(
        networkInfoRepository.networkInfo,
        trustedDevicesRepository.devices,
        devicesRepository.devices.connected,
    ) { network, trusted, connected ->
        val knownNetworks = trusted.mapNotNull { it.metadata?.lastNetworkId }.toSet()

        when {
            network == null -> FilesState.NetworkWarningUi.NoNetwork

            NetworkCapability.LOCAL_SUBNET !in network.capabilities ->
                FilesState.NetworkWarningUi.NoLocalNetwork

            connected.isNotEmpty() || knownNetworks.isEmpty() -> null

            // Android answers an ungranted SSID/BSSID read with a redacted placeholder rather than
            // a refusal, so a missing id is the location gate and not a network without one.
            // Calling that "a different network" would be a guess drawn from a permission.
            network.id == null -> unnamedNetworkWarning()

            network.id !in knownNetworks -> FilesState.NetworkWarningUi.DifferentNetwork

            else -> null
        }
    }

    /**
     * The nameless-network case, once it is known whether anything can be done about it. Nothing
     * is said when the grants are already in place: the network is then simply one Android will
     * not name, and there is no fix to offer.
     */
    private suspend fun unnamedNetworkWarning(): FilesState.NetworkWarningUi? {
        val report = requirementsChecker.forNetworkInfo()
        networkRequirements.value = report

        return FilesState.NetworkWarningUi.UnnamedNetwork.takeUnless { report.isSatisfied }
    }

    private data class Reach(
        val failure: ReachabilityFailure,
        val network: NetworkInfo?,
    ) {
        fun toUi(lastNetworkId: String?) = FilesState.UnreachableUi(
            reason = failure.reason.toUi(),
            triedLabel = failure.failedAt.relative(),
            transport = failure.transport,
            onOtherNetwork = lastNetworkId != null && network?.id != null &&
                    lastNetworkId != network.id,
        )
    }

    private data class Query(
        val filter: FilesState.FilterUi,
        val deviceId: String?,
        val sourceIds: Set<String>?,
    )

    private data class Chrome(
        val syncRequest: SyncRequestUi?,
        val syncRequestsWaiting: Int,
        val isSyncing: Boolean,
        val networkWarning: FilesState.NetworkWarningUi?,
    )

    private data class Editable(
        val selectedDeviceId: String? = null,
        val filter: FilesState.FilterUi = FilesState.FilterUi.All,
        val expandedDeviceId: String? = null,
        val syncRequestHintDismissed: Boolean = false,
    )
}

private fun SourceEntry.toFolderUi(
    pass: SourcePass?,
    entries: List<SyncFileEntry>,
): FilesState.FolderUi {
    val running = pass?.isFinished == false

    return FilesState.FolderUi(
        id = id,
        name = label,
        path = commonDirectoryOf(entries.map { it.path }),
        mode = syncMode.toUi(),
        status = when {
            running -> FilesState.FolderStatusUi.Syncing
            status is SourceEntry.Status.Pending -> FilesState.FolderStatusUi.Pending
            status is SourceEntry.Status.Disabled -> FilesState.FolderStatusUi.Disabled
            else -> FilesState.FolderStatusUi.Active
        },
        statusDetail = when {
            running -> null
            status is SourceEntry.Status.Disabled -> (status as SourceEntry.Status.Disabled).reason
            else -> lastSyncedAt?.relative()
        },
        itemCount = entries.size,
        progress = (pass as? SourcePass.Local)?.progress,
    )
}

private fun commonDirectoryOf(paths: List<String>): String? {
    val directories = paths
        .map { path -> path.substringBeforeLast('/', missingDelimiterValue = "") }
        .map { directory -> directory.split('/').filter { it.isNotEmpty() } }
        .takeIf { it.isNotEmpty() }
        ?: return null

    val shared = directories.reduce { common, segments ->
        common.zip(segments).takeWhile { (a, b) -> a == b }.map { it.first }
    }

    return shared.takeIf { it.isNotEmpty() }?.joinToString(separator = "/", prefix = "/")
}

private fun FailedContact.Reason.toUi(): FilesState.ReasonUi = when (this) {
    FailedContact.Reason.NoRoute -> FilesState.ReasonUi.NoRoute
    FailedContact.Reason.Unreachable -> FilesState.ReasonUi.Unreachable
    FailedContact.Reason.Refused -> FilesState.ReasonUi.Refused
    FailedContact.Reason.NotAllowed -> FilesState.ReasonUi.NotAllowed
    FailedContact.Reason.Failed -> FilesState.ReasonUi.Failed
}

private fun Instant.relative(): String = DateUtils.getRelativeTimeSpanString(
    toEpochMilliseconds(),
    System.currentTimeMillis(),
    DateUtils.MINUTE_IN_MILLIS,
).toString()
