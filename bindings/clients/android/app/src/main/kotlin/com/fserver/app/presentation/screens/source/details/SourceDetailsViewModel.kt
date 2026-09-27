package com.fserver.app.presentation.screens.source.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.source.details.model.SourceDetailsIntent
import com.fserver.app.presentation.screens.source.details.model.SourceDetailsState
import com.fserver.app.presentation.screens.source.details.model.SourceDetailsUiEffect
import com.fserver.app.presentation.screens.source.details.model.SourceDetailsState.ConditionUi
import com.fserver.app.presentation.screens.source.details.model.SourceDetailsState.StageKindUi
import com.fserver.app.presentation.screens.source.details.model.SourceDetailsState.StageUi
import com.fserver.app.presentation.screens.source.shared.model.SourceEndpointUi
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.app.presentation.screens.source.shared.model.latest
import com.fserver.app.presentation.screens.source.shared.model.readablePath
import com.fserver.app.presentation.screens.source.shared.model.toUi
import com.fserver.app.presentation.shared.error.ErrorReporter
import com.fserver.app.util.combineMany
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.device.model.DeviceKind
import com.fserver.core.network.device.model.LocalDevice
import com.fserver.core.network.info.NetworkInfoRepository
import com.fserver.core.network.info.model.NetworkInfo
import com.fserver.core.storage.DeviceIdentityRepository
import com.fserver.core.storage.FilesTotal
import com.fserver.core.storage.RegisteredSourcesRepository
import com.fserver.core.storage.SourceFilesTotals
import com.fserver.core.storage.TrustedDevicesRepository
import com.fserver.core.sync.SourcesController
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Instant
import java.time.Instant as JavaInstant

class SourceDetailsViewModel(
    private val key: Destination.Files.SourceDetails,
    private val sourcesController: SourcesController,
    registeredSources: RegisteredSourcesRepository,
    identity: DeviceIdentityRepository,
    trustedDevices: TrustedDevicesRepository,
    devicesRepository: DevicesRepository,
    networkInfoRepository: NetworkInfoRepository,
    private val reporter: ErrorReporter,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)

    private val effects = Channel<SourceDetailsUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    private val isSyncing = combine(
        refreshing,
        sourcesController.progress.pass(key.sourceId),
    ) { refreshing, pass -> refreshing || pass?.isFinished == false }

    private val environment = combine(
        networkInfoRepository.networkInfo,
        identity.localDevice,
    ) { network, self ->
        Environment(onMobile = network is NetworkInfo.Mobile, self = self)
    }

    val state: StateFlow<SourceDetailsState> = combineMany(
        registeredSources.observeById(key.sourceId),
        registeredSources.observeTotals(key.sourceId),
        environment,
        trustedDevices.devices,
        devicesRepository.devices.connected,
        isSyncing,
    ) { source, totals, environment, trusted, connected, syncing ->
        if (source == null) return@combineMany SourceDetailsState(isLoading = false)

        val session = connected.firstOrNull { it.deviceId == source.deviceId }
        val record = trusted.latest(source.deviceId)

        source.toState(
            totals = totals,
            environment = environment,
            peer = SourceDetailsState.PeerUi(
                id = source.deviceId,
                name = session?.displayName ?: record?.displayName ?: source.deviceId,
                kind = session?.kind ?: record?.metadata?.kind,
                online = session != null,
            ),
        ).copy(isSyncing = syncing)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SourceDetailsState(),
    )

    fun onIntent(intent: SourceDetailsIntent) {
        when (intent) {
            SourceDetailsIntent.RefreshRequested -> sync()
            SourceDetailsIntent.SendNowClicked -> sync()
            SourceDetailsIntent.DeleteConfirmed -> delete()
        }
    }

    private fun delete() {
        viewModelScope.launch {
            sourcesController.removeSource(key.sourceId).fold(
                onSuccess = { effects.send(SourceDetailsUiEffect.NavigateBack) },
                onFailure = { reporter.report(it, "Could not remove source ${key.sourceId}") },
            )
        }
    }

    private fun sync() {
        if (refreshing.value) return
        refreshing.value = true

        viewModelScope.launch {
            runCatching { sourcesController.runSync(key.sourceId, force = true) }
                .exceptionOrNull()
                ?.let { reporter.report(it, "Sync of ${key.sourceId} failed") }

            refreshing.value = false
        }
    }
}

private data class Environment(
    val onMobile: Boolean,
    val self: LocalDevice,
)

private fun SourceEntry.toState(
    totals: SourceFilesTotals,
    environment: Environment,
    peer: SourceDetailsState.PeerUi,
): SourceDetailsState {
    val mode = syncMode.toUi()
    val outgoing = syncMode is SyncMode.Mirror || role == SourceEntry.Role.Initiator
    val initiator = role == SourceEntry.Role.Initiator
    val here = if (initiator) originPath else location.readablePath()
    val there = if (initiator) null else originPath

    val stages = stagesOf(totals, mode, outgoing, hereDetail = here, peerDetail = there)
    val waiting = stages.firstOrNull { it.kind == StageKindUi.Outgoing }
        ?.takeIf { it.count > 0 && status == SourceEntry.Status.Active }
    val sendNow =
        waiting?.takeIf { preferences.deviceConstraints.wifiRequired && environment.onMobile }

    val self = SourceEndpointUi(
        name = environment.self.displayName,
        detail = here,
        deviceKind = environment.self.kind ?: DeviceKind.Phone,
    )
    // TODO: the initiator does not know where the peer stores the source - show that folder once the setup exchange reports it.
    val other = SourceEndpointUi(
        name = peer.name,
        detail = there,
        deviceKind = peer.kind,
    )

    return SourceDetailsState(
        isLoading = false,
        label = label,
        mode = mode,
        origin = if (initiator) self else other,
        target = if (initiator) other else self,
        peer = peer,
        status = when (val status = status) {
            SourceEntry.Status.Active -> SourceDetailsState.StatusUi.Active
            SourceEntry.Status.Pending -> SourceDetailsState.StatusUi.Pending
            is SourceEntry.Status.Disabled -> SourceDetailsState.StatusUi.Disabled(status.reason)
        },
        conditions = conditionsOf(syncMode, preferences),
        stages = stages,
        sendNow = sendNow?.let {
            SourceDetailsState.SendNowUi(
                count = it.count,
                bytes = it.bytes ?: 0
            )
        },
        history = SourceDetailsState.SampleHistory,
    )
}

private fun stagesOf(
    totals: SourceFilesTotals,
    mode: SourceModeUi,
    outgoing: Boolean,
    hereDetail: String?,
    peerDetail: String?,
): List<StageUi> {
    val here = totals.here.stage(StageKindUi.Here, hereDetail)
    val peer = totals.peer.stage(StageKindUi.Peer, peerDetail)
    val pending = totals.pending.stage(StageKindUi.Outgoing)

    return when {
        mode == SourceModeUi.Sync -> listOf(
            here,
            pending,
            totals.matched.stage(StageKindUi.Matched),
            peer
        )

        !outgoing -> listOf(peer, here)

        mode == SourceModeUi.Offload -> listOf(
            here,
            pending,
            peer,
            totals.evicted.stage(StageKindUi.Evicted)
        )

        else -> listOf(here, pending, peer)
    }
}

private fun FilesTotal.stage(kind: StageKindUi, detail: String? = null) = StageUi(
    kind = kind,
    count = count,
    bytes = size.bytes,
    detail = detail,
)

private fun conditionsOf(mode: SyncMode, preferences: SourceEntry.Preferences): List<ConditionUi> =
    buildList {
        add(ConditionUi.Network(wifiOnly = preferences.deviceConstraints.wifiRequired))
        if (preferences.deviceConstraints.chargingRequired) add(ConditionUi.WhileCharging)

        when (mode) {
            is SyncMode.Mirror -> add(
                ConditionUi.OnConflict(
                    keepBoth = mode.conflictResolution == SyncMode.Mirror.ConflictResolution.KeepBoth,
                )
            )

            is SyncMode.AutoUpload -> {
                mode.ignoreFilesBefore?.let { add(ConditionUi.IgnoreBefore(it.formatted())) }
                add(ConditionUi.CopiesStay)
            }

            is SyncMode.Offload -> {
                add(
                    when (val policy = mode.policy) {
                        is SyncMode.Offload.EvictPolicy.OlderThanDays ->
                            ConditionUi.EvictOlderThan(policy.days)

                        is SyncMode.Offload.EvictPolicy.LargerThanBytes ->
                            ConditionUi.EvictLargerThan(policy.bytes)
                    }
                )
                if (mode.keepPinned) add(ConditionUi.KeepPinned)
            }
        }

        preferences.fileLimits.maxFiles?.let { add(ConditionUi.MaxFiles(it)) }
        preferences.fileLimits.maxTotalSize?.let { add(ConditionUi.MaxSize(it.bytes)) }
    }

private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

private fun Instant.formatted(): String = dateFormat.format(
    JavaInstant.ofEpochMilli(toEpochMilliseconds()).atZone(ZoneId.systemDefault())
)
