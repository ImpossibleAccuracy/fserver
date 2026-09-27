package com.fserver.app.presentation.screens.source.setup.conditions

import com.fserver.app.R
import com.fserver.app.presentation.shared.error.ErrorBus
import com.fserver.app.presentation.shared.error.ErrorReporter
import com.fserver.app.presentation.model.UiText
import com.fserver.app.presentation.screens.source.setup.conditions.model.SourceConditionsIntent
import com.fserver.app.presentation.screens.source.setup.conditions.model.SourceConditionsState
import com.fserver.app.presentation.screens.source.setup.conditions.model.SourceConditionsUiEffect
import com.fserver.app.presentation.screens.source.setup.shared.SourceSetupIncompleteException
import com.fserver.app.presentation.screens.source.setup.shared.model.SourceSetupState
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.app.presentation.screens.source.shared.preferences.model.SourcePreferencesUi
import com.fserver.app.presentation.screens.source.shared.preferences.model.reduce
import com.fserver.app.presentation.screens.source.shared.preferences.model.toPreferences
import com.fserver.app.presentation.screens.source.shared.preferences.model.toSyncMode
import com.fserver.app.presentation.screens.source.shared.preferences.model.SourcePreferencesIntent
import com.fserver.app.presentation.screens.source.shared.model.SourceRoleUi
import com.fserver.app.presentation.shared.error.toAppError
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
                preferences = local.preferencesFor(mode),
                sourceFiles = shared.source?.files,
                sourceBytes = shared.source?.bytes?.bytes,
                progress = local.progress,
                progressDetail = local.progressDetail,
                error = local.error,
            )
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    fun onIntent(intent: SourceConditionsIntent) {
        when (intent) {
            SourceConditionsIntent.ExplainerAccepted ->
                editable.update { it.copy(explainerAccepted = true) }

            is SourceConditionsIntent.PreferencesChanged -> changePreferences(intent.intent)

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

    private fun changePreferences(intent: SourcePreferencesIntent) {
        val mode = flow.value.mode ?: return
        editable.update {
            it.copy(preferences = it.preferencesFor(mode).reduce(intent), preferencesMode = mode)
        }
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
        val mode = flow.value.mode
        val preferences = mode?.let { editable.value.preferencesFor(it) }
        val syncMode = mode?.let { preferences?.toSyncMode(it) }

        if (preferences == null || syncMode == null) {
            editable.update {
                it.copy(preparing = false, error = UiText.of(R.string.source_create_incomplete))
            }
            return
        }

        register(syncMode, preferences.toPreferences()).fold(
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

    private data class Editable(
        val explainerAccepted: Boolean = false,
        /** Answers for [preferencesMode]; going back and picking another mode starts them over. */
        val preferences: SourcePreferencesUi? = null,
        val preferencesMode: SourceModeUi? = null,
        val preparing: Boolean = false,
        val progress: Float = 0f,
        val progressDetail: UiText? = null,
        val error: UiText? = null,
    )

    private fun Editable.preferencesFor(mode: SourceModeUi): SourcePreferencesUi =
        preferences?.takeIf { preferencesMode == mode }
            ?: SourcePreferencesUi.build(mode, SourceRoleUi.Initiator)
}
