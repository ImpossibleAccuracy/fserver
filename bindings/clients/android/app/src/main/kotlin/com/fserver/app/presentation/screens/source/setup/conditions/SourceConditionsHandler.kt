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
import com.fserver.app.presentation.screens.source.setup.shared.SourceSetupIncompleteException
import com.fserver.app.presentation.screens.source.setup.shared.model.SourceSetupState
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.app.presentation.shared.error.toAppError
import com.fserver.common.model.FileSize
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.sync.model.SourceEntry
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
    private val register: suspend (SyncMode, SourceEntry.Preferences) -> Result<SourceEntry>,

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
                keepBoth = local.keepBoth,
                limitFiles = local.limitFiles,
                maxFiles = local.maxFiles,
                limitSize = local.limitSize,
                maxSizeGb = local.maxSizeGb,
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

            is SourceConditionsIntent.KeepBothToggled ->
                editable.update { it.copy(keepBoth = intent.enabled) }

            is SourceConditionsIntent.LimitFilesToggled ->
                editable.update { it.copy(limitFiles = intent.enabled) }

            is SourceConditionsIntent.MaxFilesStepped -> editable.update {
                val stepped = it.maxFiles + intent.steps * SourceConditionsState.MaxFilesStep
                it.copy(
                    maxFiles = stepped.coerceIn(
                        SourceConditionsState.MinMaxFiles,
                        SourceConditionsState.MaxMaxFiles,
                    )
                )
            }

            is SourceConditionsIntent.LimitSizeToggled ->
                editable.update { it.copy(limitSize = intent.enabled) }

            is SourceConditionsIntent.MaxSizeStepped -> editable.update {
                val stepped = it.maxSizeGb + intent.steps * SourceConditionsState.MaxSizeStepGb
                it.copy(
                    maxSizeGb = stepped.coerceIn(
                        SourceConditionsState.MinMaxSizeGb,
                        SourceConditionsState.MaxMaxSizeGb,
                    )
                )
            }

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

        // TODO
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

        submit()
    }

    private suspend fun submit() {
        val syncMode = flow.value.mode?.let(::toSyncMode)

        if (syncMode == null) {
            editable.update {
                it.copy(preparing = false, error = UiText.of(R.string.source_create_incomplete))
            }
            return
        }

        register(syncMode, toPreferences()).fold(
            onSuccess = { entry ->
                editable.update { it.copy(preparing = false) }
                effectChannel.send(SourceConditionsUiEffect.NavigateToProgress(entry.id))
            },
            onFailure = { failure ->
                val error = if (failure is SourceSetupIncompleteException) {
                    UiText.of(R.string.source_create_incomplete)
                } else {
                    reporter.report(failure, "Could not register the source")
                    failure.toAppError().message
                }

                editable.update { it.copy(preparing = false, error = error) }
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

    private fun toPreferences(): SourceEntry.Preferences {
        val form = editable.value

        return SourceEntry.Preferences(
            deviceConstraints = SourceEntry.Preferences.DeviceConstraints(
                wifiRequired = form.wifiOnly,
                chargingRequired = form.chargingOnly,
            ),
            conflictResolution = if (form.keepBoth) {
                SourceEntry.Preferences.ConflictResolution.KeepBoth
            } else {
                SourceEntry.Preferences.ConflictResolution.LastWriteWins
            },
            fileLimits = SourceEntry.Preferences.FileLimits(
                maxFiles = form.maxFiles.takeIf { form.limitFiles },
                maxTotalSize = FileSize(form.maxSizeGb.toLong() * BytesInGb)
                    .takeIf { form.limitSize },
            ),
        )
    }

    private data class Editable(
        val explainerAccepted: Boolean = false,
        val uploadScope: UploadScopeUi = UploadScopeUi.New,
        val wifiOnly: Boolean = true,
        val chargingOnly: Boolean = false,
        val keepBoth: Boolean = false,
        val limitFiles: Boolean = false,
        val maxFiles: Int = SourceConditionsState.DefaultMaxFiles,
        val limitSize: Boolean = false,
        val maxSizeGb: Int = SourceConditionsState.DefaultMaxSizeGb,
        val criterion: EvictCriterionUi = EvictCriterionUi.OlderThanDays,
        val olderThanDays: Int = SourceConditionsState.DefaultDays,
        val keepPinned: Boolean = true,
        val hostRights: HostRightsUi = HostRightsUi.ReadOnly,
        val preparing: Boolean = false,
        val progress: Float = 0f,
        val progressDetail: UiText? = null,
        val error: UiText? = null,
    )

    private companion object {
        const val BytesInGb = 1024L * 1024 * 1024
    }
}
