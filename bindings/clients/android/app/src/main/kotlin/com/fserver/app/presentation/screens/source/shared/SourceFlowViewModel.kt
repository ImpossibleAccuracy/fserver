package com.fserver.app.presentation.screens.source.shared

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.screens.source.access.model.SourceAccessIntent
import com.fserver.app.presentation.screens.source.access.model.SourceAccessState
import com.fserver.app.presentation.screens.source.conditions.model.SourceConditionsIntent
import com.fserver.app.presentation.screens.source.conditions.model.SourceConditionsState
import com.fserver.app.presentation.screens.source.conditions.model.SourceConditionsUiEffect
import com.fserver.app.presentation.screens.source.mode.model.SourceModeIntent
import com.fserver.app.presentation.screens.source.mode.model.SourceModeState
import com.fserver.app.presentation.screens.source.pick.model.SourcePickIntent
import com.fserver.app.presentation.screens.source.pick.model.SourcePickState
import com.fserver.app.presentation.screens.source.access.SourceAccessGrant
import com.fserver.app.presentation.screens.source.shared.composable.SourceAccessUi
import com.fserver.app.presentation.screens.source.shared.composable.SourceKindUi
import com.fserver.app.presentation.screens.source.shared.composable.SourceModeUi
import com.fserver.app.presentation.screens.source.shared.model.PickedSourceUi
import com.fserver.app.presentation.screens.source.shared.model.SourceFlowState
import com.fserver.app.presentation.screens.source.shared.model.SourceSummaryUi
import com.fserver.core.files.model.FoundDirectory
import com.fserver.core.files.scan.DirectoryScanner
import com.fserver.core.files.scan.impl.DirectoryScannerImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import timber.log.Timber

class SourceFlowViewModel(
    context: Context,
) : ViewModel() {

    private val directoryScanner: DirectoryScanner = DirectoryScannerImpl(context)

    private val editable = MutableStateFlow(SourceFlowState())

    val isStarted: StateFlow<Boolean> = editable
        .map { it.kind != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = editable.value.kind != null,
        )

    val pickState: StateFlow<SourcePickState> = editable
        .map { SourcePickState(moreExpanded = it.moreExpanded) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SourcePickState())

    val accessState: StateFlow<SourceAccessState?> = editable
        .map { it.toAccessState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val modeState: StateFlow<SourceModeState?> = editable
        .map { it.toModeState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val conditionsState: StateFlow<SourceConditionsState?> = editable
        .map { it.toConditionsState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val conditionsEffectChannel = Channel<SourceConditionsUiEffect>(Channel.BUFFERED)
    val conditionsEffects = conditionsEffectChannel.receiveAsFlow()

    private var scanJob: Job? = null
    private var prepareJob: Job? = null

    fun start(kind: SourceKindUi) {
        editable.value = SourceFlowState(kind = kind)
        scanJob?.cancel()
        prepareJob?.cancel()
    }

    fun onDeviceSelected(deviceId: String) {
        //editable.update { it.copy(selectedDeviceId = deviceId) }
    }

    fun onPickIntent(intent: SourcePickIntent) {
        when (intent) {
            SourcePickIntent.MoreToggled ->
                editable.update { it.copy(moreExpanded = !it.moreExpanded) }
        }
    }

    fun onAccessIntent(intent: SourceAccessIntent) {
        when (intent) {
            is SourceAccessIntent.AccessAnswered -> when (intent.grant) {
                SourceAccessGrant.Denied ->
                    editable.update { it.copy(accessPhase = SourceAccessState.Phase.Denied) }

                else -> startScan(intent.grant)
            }

            SourceAccessIntent.ScanCancelled -> {
                scanJob?.cancel()
                editable.update { it.copy(accessPhase = SourceAccessState.Phase.Explaining) }
            }
        }
    }

    fun onModeIntent(intent: SourceModeIntent) {
        when (intent) {
            is SourceModeIntent.ModeSelected -> editable.update { it.copy(mode = intent.mode) }

            SourceModeIntent.ChangeSelectionClicked -> Unit
        }
    }

    fun onConditionsIntent(intent: SourceConditionsIntent) {
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
                prepareJob = viewModelScope.launch { prepare() }
            }

            SourceConditionsIntent.PreparingCancelled -> {
                prepareJob?.cancel()
                editable.update { it.copy(preparing = false) }
            }
        }
    }

    private fun startScan(grant: SourceAccessGrant) {
        scanJob?.cancel()
        editable.update {
            it.copy(
                accessPhase = SourceAccessState.Phase.Scanning,
                scanPath = grant.scanPath(),
                scannedFiles = 0,
                scannedBytes = 0,
                source = null,
            )
        }

        scanJob = viewModelScope.launch {
            val directory = grant.directory() ?: return@launch

            runCatching { directoryScanner.scan(directory).collect { onScanState(it, grant) } }
                .onFailure { error ->
                    Timber.e(error, "Failed to scan %s", directory)
                    editable.update { it.copy(accessPhase = SourceAccessState.Phase.Denied) }
                }
        }
    }

    private fun onScanState(scan: DirectoryScanner.State, grant: SourceAccessGrant) {
        when (scan) {
            is DirectoryScanner.State.Progress -> editable.update {
                it.copy(scannedFiles = scan.scannedFiles, scannedBytes = scan.scannedSize.bytes)
            }

            is DirectoryScanner.State.Ready -> {
                val bytes = scan.files.sumOf { file -> file.size.bytes }
                val tree = grant as? SourceAccessGrant.Tree
                val access = (grant as? SourceAccessGrant.Media)?.access ?: SourceAccessUi.Full

                editable.update {
                    it.copy(
                        accessPhase = SourceAccessState.Phase.Scanned,
                        access = access,
                        scannedFiles = scan.files.size,
                        scannedBytes = bytes,
                        source = PickedSourceUi(
                            files = scan.files.size,
                            bytes = bytes,
                            uri = tree?.uri?.toString(),
                            label = tree?.label.orEmpty(),
                        ),
                    )
                }
            }
        }
    }

    private suspend fun prepare() {
        editable.update { it.copy(preparing = true, prepareProgress = 0f, prepareDetail = "") }

        repeat(PrepareSteps) { step ->
            delay(PrepareStepMillis)
            val done = step + 1
            editable.update {
                it.copy(
                    prepareProgress = done.toFloat() / PrepareSteps,
                    prepareDetail = if (it.mode == SourceModeUi.Offload) {
                        "found ${done * 214} · ${done * 184 / 100.0} GB"
                    } else {
                        "${done * 340} of $SampleBacklog"
                    },
                )
            }
        }

        val summary = editable.value.toSummary() ?: return
        conditionsEffectChannel.send(SourceConditionsUiEffect.NavigateToDone(summary))
    }

    private companion object {
        const val PrepareSteps = 10
        const val PrepareStepMillis = 120L

        const val SampleTarget = "HOME-NAS"
        const val SampleFolder = "DCIM/Projects"
        const val SampleBacklog = "3,402"
    }
}

private fun SourceAccessGrant.scanPath(): String = (this as? SourceAccessGrant.Tree)?.label ?: ""

private fun SourceAccessGrant.directory(): FoundDirectory? = when (this) {
    SourceAccessGrant.Denied -> null
    SourceAccessGrant.AllFiles -> FoundDirectory.Root("/")
    is SourceAccessGrant.Tree -> FoundDirectory.Path(uri.toString())
    is SourceAccessGrant.Media -> FoundDirectory.Media
}
