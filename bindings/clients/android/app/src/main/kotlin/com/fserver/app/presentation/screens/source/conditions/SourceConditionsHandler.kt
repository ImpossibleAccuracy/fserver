package com.fserver.app.presentation.screens.source.conditions

import com.fserver.app.presentation.screens.source.conditions.model.EvictCriterionUi
import com.fserver.app.presentation.screens.source.conditions.model.HostRightsUi
import com.fserver.app.presentation.screens.source.conditions.model.SourceConditionsIntent
import com.fserver.app.presentation.screens.source.conditions.model.SourceConditionsState
import com.fserver.app.presentation.screens.source.conditions.model.SourceConditionsUiEffect
import com.fserver.app.presentation.screens.source.conditions.model.UploadScopeUi
import com.fserver.app.presentation.screens.source.shared.model.SourceFlowState
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.core.network.device.DevicesRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class SourceConditionsHandler(
    private val devicesRepository: DevicesRepository,

    private val flow: MutableStateFlow<SourceFlowState>,
    private val scope: CoroutineScope,
) {
    private var prepareJob: Job? = null

    private val editable = MutableStateFlow(Editable())

    private val effectChannel = Channel<SourceConditionsUiEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private val targetDevice = flow.map { it.targetDeviceId }
        .flatMapLatest {
            if (it == null) flowOf(null)
            else devicesRepository.device(it)
        }

    val state: StateFlow<SourceConditionsState?> =
        combine(flow, editable, targetDevice) { shared, local, device ->
            val mode = shared.mode ?: return@combine null

            SourceConditionsState(
                kind = shared.kind ?: return@combine null,
                mode = mode,
                phase = when {
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
        editable.update { it.copy(preparing = true, progress = 0f, progressDetail = "") }

        val mode = flow.value.mode
        val steps = 5

        repeat(steps) { step ->
            delay(100)
            val done = step + 1
            editable.update {
                it.copy(
                    progress = done.toFloat() / steps,
                    progressDetail = if (mode == SourceModeUi.Offload) {
                        "found ${done * 214} · ${done * 184 / 100.0} GB"
                    } else {
                        "${done * 340} of ${flow.value.source?.files}"
                    },
                )
            }
        }

        flow.update {
            it.copy(
                conditions = SourceFlowState.SavedConditions(
                    olderThanDays = editable.value.olderThanDays,
                    /*uploadScope = editable.value.uploadScope,
                    wifiOnly = editable.value.wifiOnly,
                    chargingOnly = editable.value.chargingOnly,
                    criterion = editable.value.criterion,
                    keepPinned = editable.value.keepPinned,
                    hostRights = editable.value.hostRights,*/
                )
            )
        }

        effectChannel.send(SourceConditionsUiEffect.NavigateToDone)
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
        val progressDetail: String = "",
    )
}
