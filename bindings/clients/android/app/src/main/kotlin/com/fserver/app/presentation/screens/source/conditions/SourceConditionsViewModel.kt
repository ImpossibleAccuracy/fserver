package com.fserver.app.presentation.screens.source.conditions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.screens.source.shared.SourceKindUi
import com.fserver.app.presentation.screens.source.shared.SourceModeUi
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.source.conditions.model.SourceConditionsIntent
import com.fserver.app.presentation.screens.source.conditions.model.SourceConditionsState
import com.fserver.app.presentation.screens.source.conditions.model.SourceConditionsUiEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * TODO: the form is real, the work behind it is not. Confirming runs a timer instead of asking
 * `:core` what the target already holds or what the rule would evict, and nothing is persisted
 * — the source is forgotten the moment the flow ends.
 */
class SourceConditionsViewModel(
    key: Destination.Source.Conditions,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SourceConditionsState(
            kind = key.kind,
            mode = key.mode,
            // Offload is the only mode that has to explain itself before it can be configured.
            phase = if (key.mode == SourceModeUi.Offload) {
                SourceConditionsState.Phase.Explainer
            } else {
                SourceConditionsState.Phase.Form
            },
            targetName = SampleTarget,
            sourceLabel = if (key.kind == SourceKindUi.Photos) "" else SampleFolder,
            backlogLabel = SampleBacklog,
        )
    )
    val state: StateFlow<SourceConditionsState> = _state.asStateFlow()

    private val effects = Channel<SourceConditionsUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    private var prepareJob: Job? = null

    fun onIntent(intent: SourceConditionsIntent) {
        when (intent) {
            SourceConditionsIntent.ExplainerAccepted ->
                _state.update { it.copy(phase = SourceConditionsState.Phase.Form) }

            is SourceConditionsIntent.UploadScopeSelected ->
                _state.update { it.copy(uploadScope = intent.scope) }

            is SourceConditionsIntent.WifiOnlyToggled ->
                _state.update { it.copy(wifiOnly = intent.enabled) }

            is SourceConditionsIntent.ChargingOnlyToggled ->
                _state.update { it.copy(chargingOnly = intent.enabled) }

            is SourceConditionsIntent.CriterionSelected ->
                _state.update { it.copy(criterion = intent.criterion) }

            is SourceConditionsIntent.DaysStepped -> _state.update {
                val stepped = it.olderThanDays + intent.steps * SourceConditionsState.DaysStep
                it.copy(
                    olderThanDays = stepped.coerceIn(
                        SourceConditionsState.MinDays,
                        SourceConditionsState.MaxDays,
                    )
                )
            }

            is SourceConditionsIntent.KeepPinnedToggled ->
                _state.update { it.copy(keepPinned = intent.enabled) }

            is SourceConditionsIntent.HostRightsSelected ->
                _state.update { it.copy(hostRights = intent.rights) }

            SourceConditionsIntent.Confirmed -> {
                prepareJob?.cancel()
                prepareJob = viewModelScope.launch { prepare() }
            }

            // Cancelling drops back to the form: the answers are still good, only the run was
            // abandoned. Nothing was turned on yet, so there is nothing to undo.
            SourceConditionsIntent.PreparingCancelled -> {
                prepareJob?.cancel()
                _state.update { it.copy(phase = SourceConditionsState.Phase.Form) }
            }
        }
    }

    private suspend fun prepare() {
        _state.update {
            it.copy(
                phase = SourceConditionsState.Phase.Preparing,
                progress = 0f,
                progressDetail = "",
            )
        }

        repeat(PrepareSteps) { step ->
            delay(PrepareStepMillis)
            val done = step + 1
            _state.update {
                it.copy(
                    progress = done.toFloat() / PrepareSteps,
                    // The numbers run up during the step: on the offload path this is the only
                    // preview of consequences the user gets before an irreversible mode starts.
                    progressDetail = if (it.mode == SourceModeUi.Offload) {
                        "found ${done * 214} · ${done * 184 / 100.0} GB"
                    } else {
                        "${done * 340} of $SampleBacklog"
                    },
                )
            }
        }

        effects.send(SourceConditionsUiEffect.NavigateToDone)
    }

    private companion object {
        const val PrepareSteps = 10
        const val PrepareStepMillis = 120L

        const val SampleTarget = "HOME-NAS"
        const val SampleFolder = "DCIM/Projects"
        const val SampleBacklog = "3,402"
    }
}
