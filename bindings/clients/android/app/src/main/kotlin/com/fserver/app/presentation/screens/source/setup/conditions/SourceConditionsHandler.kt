package com.fserver.app.presentation.screens.source.setup.conditions

import com.fserver.app.R
import com.fserver.app.presentation.shared.error.ErrorBus
import com.fserver.app.presentation.shared.error.ErrorReporter
import com.fserver.app.presentation.model.UiText
import com.fserver.app.presentation.screens.source.setup.conditions.model.EvictCriterionUi
import com.fserver.app.presentation.screens.source.setup.conditions.model.HostRightsUi
import com.fserver.app.presentation.screens.source.setup.conditions.model.SourceConditionsIntent
import com.fserver.app.presentation.screens.source.setup.conditions.model.SourceConditionsState
import com.fserver.app.presentation.screens.source.setup.conditions.model.SourceConditionsUiEffect
import com.fserver.app.presentation.screens.source.setup.conditions.model.UploadScopeUi
import com.fserver.app.presentation.screens.source.setup.shared.model.SourceSetupState
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.app.presentation.shared.error.toAppError
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.sync.SourcesController
import com.fserver.core.sync.model.SyncMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class SourceConditionsHandler(
    private val devicesRepository: DevicesRepository,
    private val sourcesController: SourcesController,

    private val flow: MutableStateFlow<SourceSetupState>,
    private val scope: CoroutineScope,
    private val reporter: ErrorReporter,
) {
    private var prepareJob: Job? = null

    private val editable = MutableStateFlow(Editable())

    private val effectChannel = Channel<SourceConditionsUiEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private val targetDevice = flow.map { it.targetDeviceId }
        .flatMapLatest {
            if (it == null) flowOf(null)
            else devicesRepository.devices.device(it)
        }

    val state: StateFlow<SourceConditionsState?> =
        combine(flow, editable, targetDevice) { shared, local, device ->
            val mode = shared.mode ?: return@combine null

            SourceConditionsState(
                kind = shared.kind ?: return@combine null,
                mode = mode,
                phase = when {
                    local.error != null -> SourceConditionsState.Phase.Failed
                    local.preparing -> SourceConditionsState.Phase.Preparing
                    mode == SourceModeUi.Offload && !local.explainerAccepted ->
                        SourceConditionsState.Phase.Explainer

                    else -> SourceConditionsState.Phase.Form
                },
                targetName = device?.displayName ?: "",
                sourceLabel = shared.source?.label ?: "",
                uploadScope = local.uploadScope,
                backlogLabel = when (local.uploadScope) {
                    UploadScopeUi.New -> null
                    UploadScopeUi.All -> (shared.source?.files ?: 0).toString()
                },
                wifiOnly = local.wifiOnly,
                chargingOnly = local.chargingOnly,
                criterion = local.criterion,
                olderThanDays = local.olderThanDays,
                keepPinned = local.keepPinned,
                hostRights = local.hostRights,
                progress = local.progress,
                progressDetail = local.progressDetail,
                error = local.error,
            )
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    fun onIntent(intent: SourceConditionsIntent) {
        when (intent) {
            SourceConditionsIntent.ExplainerAccepted ->
                editable.update { it.copy(explainerAccepted = true) }

            is SourceConditionsIntent.UploadScopeSelected ->
                editable.update { it.copy(uploadScope = intent.scope) }

            is SourceConditionsIntent.WifiOnlyToggled ->
                editable.update { it.copy(wifiOnly = intent.enabled) }

            is SourceConditionsIntent.ChargingOnlyToggled ->
                editable.update { it.copy(chargingOnly = intent.enabled) }

            is SourceConditionsIntent.CriterionSelected ->
                editable.update { it.copy(criterion = intent.criterion) }

            is SourceConditionsIntent.DaysStepped -> editable.update {
                val stepped = it.olderThanDays + intent.steps * SourceConditionsState.DaysStep
                it.copy(
                    olderThanDays = stepped.coerceIn(
                        SourceConditionsState.MinDays,
                        SourceConditionsState.MaxDays,
                    )
                )
            }

            is SourceConditionsIntent.KeepPinnedToggled ->
                editable.update { it.copy(keepPinned = intent.enabled) }

            is SourceConditionsIntent.HostRightsSelected ->
                editable.update { it.copy(hostRights = intent.rights) }

            SourceConditionsIntent.Confirmed -> {
                prepareJob?.cancel()
                prepareJob = scope.launch { prepare() }
            }

            SourceConditionsIntent.RetryConfirmed -> {
                prepareJob?.cancel()
                editable.update { it.copy(error = null) }
                prepareJob = scope.launch { prepare() }
            }

            SourceConditionsIntent.PreparingCancelled -> {
                prepareJob?.cancel()
                editable.update { it.copy(preparing = false) }
            }
        }
    }

    fun reset() {
        prepareJob?.cancel()
        editable.value = Editable()
    }

    private suspend fun prepare() {
        editable.update {
            it.copy(preparing = true, progress = 0f, progressDetail = null, error = null)
        }

        val mode = flow.value.mode
        val steps = 5

        repeat(steps) { step ->
            delay(100)
            val done = step + 1
            editable.update {
                it.copy(
                    progress = done.toFloat() / steps,
                    progressDetail = if (mode == SourceModeUi.Offload) {
                        UiText.of(
                            R.string.source_prepare_detail_offload,
                            done * 214,
                            done * 184 / 100.0,
                        )
                    } else {
                        UiText.of(
                            R.string.source_progress_detail,
                            done * 340,
                            flow.value.source?.files ?: 0,
                        )
                    },
                )
            }
        }

        register()
    }

    /**
     * Hands the answered form to the engine, which assigns the source its id and asks the peer to
     * host the other half. Everything after this is the peer's move, so it happens on the upload
     * screen rather than here.
     */
    private suspend fun register() {
        val shared = flow.value
        val source = shared.source
        val deviceId = shared.targetDeviceId
        val syncMode = shared.mode?.let(::toSyncMode)

        if (source == null || deviceId == null || syncMode == null) {
            editable.update {
                it.copy(preparing = false, error = UiText.of(R.string.source_create_incomplete))
            }
            return
        }

        sourcesController.addSource(
            location = source.location,
            syncMode = syncMode,
            deviceId = deviceId,
            label = source.label.ifEmpty { shared.kind?.name.orEmpty() },
        ).fold(
            onSuccess = { entry ->
                editable.update { it.copy(preparing = false) }
                effectChannel.send(SourceConditionsUiEffect.NavigateToProgress(entry.id))
            },
            onFailure = { failure ->
                reporter.report(failure, "Could not register the source")
                editable.update {
                    it.copy(preparing = false, error = failure.toAppError().message)
                }
            },
        )
    }

    /**
     * The form, as the engine reads it. Auto-upload scoped to new files is a cut-off rather than a
     * filter, which is why the backlog answer becomes an instant.
     */
    private fun toSyncMode(mode: SourceModeUi): SyncMode? = when (mode) {
        SourceModeUi.Sync -> SyncMode.Mirror

        SourceModeUi.AutoUpload -> SyncMode.AutoUpload(
            ignoreFilesBefore = when (editable.value.uploadScope) {
                UploadScopeUi.New -> Clock.System.now()
                UploadScopeUi.All -> null
            },
        )

        // The engine evicts by age only, so the least-recently-used rule falls back to the same
        // cut-off until it grows a policy of its own.
        SourceModeUi.Offload -> SyncMode.Offload(
            policy = SyncMode.Offload.EvictPolicy.OlderThanDays(editable.value.olderThanDays),
            keepPinned = editable.value.keepPinned,
        )

        SourceModeUi.Host -> null
    }

    private data class Editable(
        val explainerAccepted: Boolean = false,
        val uploadScope: UploadScopeUi = UploadScopeUi.New,
        val wifiOnly: Boolean = true,
        val chargingOnly: Boolean = false,
        val criterion: EvictCriterionUi = EvictCriterionUi.OlderThanDays,
        val olderThanDays: Int = SourceConditionsState.DefaultDays,
        val keepPinned: Boolean = true,
        val hostRights: HostRightsUi = HostRightsUi.ReadOnly,
        val preparing: Boolean = false,
        val progress: Float = 0f,
        val progressDetail: UiText? = null,
        val error: UiText? = null,
    )
}
