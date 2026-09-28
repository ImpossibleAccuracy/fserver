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
import com.fserver.core.sync.SourcesController
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import com.fserver.core.sync.progress.IndexingProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class SourceConditionsHandler(
    private val devicesRepository: DevicesRepository,
    private val sourcesController: SourcesController,
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
        val preferences = mode?.let { editable.value.preferencesFor(it) }
        val syncMode = mode?.let { preferences?.toSyncMode(it) }

        if (preferences == null || syncMode == null) {
            editable.update {
                it.copy(preparing = false, error = UiText.of(R.string.source_create_incomplete))
            }
            return
        }

        val entry = withContext(NonCancellable) { register(syncMode, preferences.toPreferences()) }
            .getOrElse { failure ->
                val error = if (failure is SourceSetupIncompleteException) {
                    UiText.of(R.string.source_create_incomplete)
                } else {
                    reporter.report(failure, "Could not register the source")
                    failure.toAppError().message
                }

                editable.update { it.copy(preparing = false, error = error) }
                return
            }

        try {
            currentCoroutineContext().ensureActive()
            index(entry.id)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { sourcesController.removeSource(entry.id) }
            throw e
        }

        editable.update { it.copy(preparing = false) }
        effectChannel.send(SourceConditionsUiEffect.NavigateToProgress(entry.id))
    }

    private suspend fun index(sourceId: String) = coroutineScope {
        val watcher = launch {
            sourcesController.progress.indexing(sourceId)
                .filterNotNull()
                .collect { run -> editable.update { it.withIndexing(run) } }
        }

        sourcesController.index(sourceId)
            .onFailure { reporter.report(it, "Could not index the new source") }

        watcher.cancel()
    }

    private fun Editable.withIndexing(run: IndexingProgress): Editable {
        val total = flow.value.source?.files ?: 0

        return when (run.stage) {
            IndexingProgress.Stage.Hashing -> copy(
                progress = run.filesHashed.toFloat() / run.filesToHash.coerceAtLeast(1),
                progressDetail = UiText.of(R.string.source_progress_hashing_detail, run.filesHashed, run.filesToHash),
            )

            else -> copy(
                progress = if (total > 0) (run.filesScanned.toFloat() / total).coerceAtMost(1f) else 0f,
                progressDetail = if (flow.value.mode == SourceModeUi.Offload) {
                    UiText.of(R.string.source_prepare_detail_offload, run.filesScanned, run.bytesScanned / BytesPerGb)
                } else {
                    UiText.of(R.string.source_progress_detail, run.filesScanned, total)
                },
            )
        }
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

private const val BytesPerGb = 1_000_000_000.0
