package com.fserver.app.presentation.screens.source.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.data.export.ArchiveExporter
import com.fserver.app.presentation.composable.model.PeerUi
import com.fserver.app.presentation.composable.model.dateLabel
import com.fserver.app.presentation.composable.model.fileName
import com.fserver.app.presentation.composable.model.peerOf
import com.fserver.app.presentation.composable.model.peers
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.model.UiText
import com.fserver.app.presentation.screens.source.details.model.SourceDetailsIntent
import com.fserver.app.presentation.screens.source.details.model.SourceDetailsState
import com.fserver.app.presentation.screens.source.details.model.SourceDetailsState.ConditionUi
import com.fserver.app.presentation.screens.source.details.model.SourceDetailsState.StageKindUi
import com.fserver.app.presentation.screens.source.details.model.SourceDetailsState.StageUi
import com.fserver.app.presentation.screens.source.details.model.SourceDetailsUiEffect
import com.fserver.app.presentation.screens.source.shared.model.SourceEndpointUi
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.app.presentation.screens.source.shared.model.ownHalfOf
import com.fserver.app.presentation.screens.source.shared.model.peerHalfOf
import com.fserver.app.presentation.screens.source.shared.model.storageLabel
import com.fserver.app.presentation.screens.source.shared.model.toUi
import com.fserver.app.presentation.shared.error.ErrorReporter
import com.fserver.app.presentation.shared.export.ExportTrigger
import com.fserver.app.presentation.shared.sync.SyncTrigger
import com.fserver.app.util.combineMany
import com.fserver.app.util.stateInScreen
import com.fserver.core.crypto.EncryptionController
import com.fserver.core.crypto.EncryptionStatus
import com.fserver.core.crypto.model.EncryptionPolicy
import com.fserver.core.files.FilesController
import com.fserver.core.files.SyncFileEntry
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.device.model.DeviceKind
import com.fserver.core.network.device.model.LocalDevice
import com.fserver.core.network.info.NetworkInfoRepository
import com.fserver.core.network.info.model.NetworkInfo
import com.fserver.core.storage.DeviceIdentityRepository
import com.fserver.core.storage.FilesTotal
import com.fserver.core.storage.RegisteredSourcesRepository
import com.fserver.core.storage.SourceFilesTotals
import com.fserver.core.sync.SourcesController
import com.fserver.core.sync.conflict.ConflictsController
import com.fserver.core.sync.conflict.FileConflict
import com.fserver.core.sync.metadata.PeerSourceMetadata
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import com.fserver.core.sync.model.drivesSync
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SourceDetailsViewModel(
    private val key: Destination.Files.SourceDetails,
    private val sourcesController: SourcesController,
    conflictsController: ConflictsController,
    filesController: FilesController,
    registeredSources: RegisteredSourcesRepository,
    identity: DeviceIdentityRepository,
    devicesRepository: DevicesRepository,
    networkInfoRepository: NetworkInfoRepository,
    encryptionController: EncryptionController,
    archiveExporter: ArchiveExporter,
    private val reporter: ErrorReporter,
) : ViewModel() {

    private val syncTrigger = SyncTrigger(viewModelScope, sourcesController, reporter)
    private val exportTrigger = ExportTrigger(viewModelScope, archiveExporter)
    val exportResults = exportTrigger.results

    private val effects = Channel<SourceDetailsUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    private val isSyncing = combine(
        syncTrigger.running,
        sourcesController.progress.pass(key.sourceId),
    ) { refreshing, pass -> refreshing || pass?.isFinished == false }

    private val environment = combine(
        networkInfoRepository.networkInfo,
        identity.localDevice,
    ) { network, self ->
        Environment(onMobile = network is NetworkInfo.Mobile, self = self)
    }

    private val issues = combine(
        conflictsController.pending,
        filesController.overallContent,
    ) { conflicts, files ->
        Issues(
            conflicts = conflicts.filter { it.sourceId == key.sourceId },
            lost = files.filter { it.sourceId == key.sourceId && it.lostOnPeer },
        )
    }

    private val encryption = registeredSources.observeById(key.sourceId).flatMapLatest { source ->
        source?.let(encryptionController::status)?.map { it.toCondition() } ?: flowOf(null)
    }

    val state: StateFlow<SourceDetailsState> = combine(
        combineMany(
        registeredSources.observeById(key.sourceId),
        registeredSources.observeTotals(key.sourceId),
        environment,
        devicesRepository.peers(),
        isSyncing,
        issues,
        registeredSources.metadata,
    ) { source, totals, environment, peers, syncing, issues, metadata ->
        if (source == null) return@combineMany SourceDetailsState(isLoading = false)

        source.toState(
            totals = totals,
            environment = environment,
            peer = peers.peerOf(source.deviceId),
            metadata = metadata,
        ).copy(
            isSyncing = syncing,
            attention = issues.toAttention(source.syncMode) + peerFullness(source, metadata),
        )
    }, encryption
    ) { state, encryption ->
        state.copy(conditions = state.conditions + listOfNotNull(encryption))
    }.combine(exportTrigger.running) { state, export -> state.copy(export = export) }
        .stateInScreen(viewModelScope, SourceDetailsState())

    fun onIntent(intent: SourceDetailsIntent) {
        when (intent) {
            SourceDetailsIntent.RefreshRequested,
            SourceDetailsIntent.SendNowClicked -> syncTrigger.run(
                "Sync of ${key.sourceId} failed",
                key.sourceId
            )

            SourceDetailsIntent.DeleteConfirmed -> delete()
            is SourceDetailsIntent.Export -> exportTrigger.run(intent.uri, setOf(key.sourceId))
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
}

private fun EncryptionStatus.toCondition(): ConditionUi? = when (this) {
    EncryptionStatus.Unsupported -> ConditionUi.NotEncryptable
    EncryptionStatus.Off -> null
    is EncryptionStatus.Encrypted -> ConditionUi.Encrypted
    is EncryptionStatus.Migrating ->
        if (toward is EncryptionPolicy.Required) ConditionUi.Encrypting(remaining) else ConditionUi.Decrypting(
            remaining
        )
}

private data class Issues(
    val conflicts: List<FileConflict>,
    val lost: List<SyncFileEntry>,
)

private fun Issues.toAttention(mode: SyncMode): List<SourceDetailsState.AttentionUi> = buildList {
    if (conflicts.isNotEmpty()) {
        add(
            SourceDetailsState.AttentionUi.Conflicts(
                conflicts.size,
                conflicts.map { it.path.fileName() })
        )
    }
    if (mode is SyncMode.Offload && lost.isNotEmpty()) {
        add(SourceDetailsState.AttentionUi.LostOnPeer(lost.size, lost.map { it.path.fileName() }))
    }
}

private const val PeerAlmostFullPercent = 90f

private fun peerFullness(
    source: SourceEntry,
    metadata: List<PeerSourceMetadata>,
): List<SourceDetailsState.AttentionUi> {
    if (!source.drivesSync) return emptyList()

    val peer = metadata.peerHalfOf(source) ?: return emptyList()
    val used = peer.usedPercent?.takeIf { it >= PeerAlmostFullPercent } ?: return emptyList()

    return listOf(
        SourceDetailsState.AttentionUi.PeerAlmostFull(
            used,
            peer.usage.files,
            peer.usage.bytes
        )
    )
}

private data class Environment(
    val onMobile: Boolean,
    val self: LocalDevice,
)

private fun SourceEntry.toState(
    totals: SourceFilesTotals,
    environment: Environment,
    peer: PeerUi,
    metadata: List<PeerSourceMetadata>,
): SourceDetailsState {
    val mode = syncMode.toUi()
    val outgoing = drivesSync
    val initiator = role == SourceEntry.Role.Initiator
    val here = metadata.ownHalfOf(this)?.storageLabel()
    val there = metadata.peerHalfOf(this)?.storageLabel()

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
    val other = SourceEndpointUi(
        name = peer.name,
        detail = there,
        deviceKind = peer.kind,
    )

    return SourceDetailsState(
        isLoading = false,
        label = label,
        mode = mode,
        canSync = drivesSync,
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
    hereDetail: UiText?,
    peerDetail: UiText?,
): List<StageUi> {
    val here = totals.here.stage(StageKindUi.Here, hereDetail)
    val peer = totals.peer.stage(StageKindUi.Peer, peerDetail)
    val pending = totals.pending.stage(StageKindUi.Outgoing)

    return when {
        mode == SourceModeUi.Sync -> listOf(
            here,
            pending,
            totals.matched.stage(StageKindUi.Matched),
            totals.incoming.stage(StageKindUi.Incoming),
            peer
        )

        !outgoing -> listOf(peer, here)

        mode == SourceModeUi.Offload || mode == SourceModeUi.Host -> listOf(
            here,
            pending,
            peer,
            totals.evicted.stage(StageKindUi.Evicted)
        )

        else -> listOf(here, pending, peer)
    }
}

private fun FilesTotal.stage(kind: StageKindUi, detail: UiText? = null) = StageUi(
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
                    ask = mode.conflictResolution == SyncMode.Mirror.ConflictResolution.Ask,
                )
            )

            is SyncMode.AutoUpload -> {
                mode.ignoreFilesBefore?.let { add(ConditionUi.IgnoreBefore(it.dateLabel())) }
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
            }

            SyncMode.Host -> Unit
        }

        preferences.fileLimits.maxFiles?.let { add(ConditionUi.MaxFiles(it)) }
        preferences.fileLimits.maxTotalSize?.let { add(ConditionUi.MaxSize(it.bytes)) }
    }
