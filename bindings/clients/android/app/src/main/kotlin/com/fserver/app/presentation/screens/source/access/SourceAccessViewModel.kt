package com.fserver.app.presentation.screens.source.access

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.composable.model.SourceAccessUi
import com.fserver.app.presentation.composable.model.SourceKindUi
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.source.access.model.SourceAccessIntent
import com.fserver.app.presentation.screens.source.access.model.SourceAccessState
import com.fserver.app.presentation.screens.source.access.model.SourceAccessUiEffect
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
 * Explain, ask, report — the shape every branch shares.
 *
 * TODO: nothing here talks to Android yet. [SourceAccessIntent.AccessRequested] answers itself,
 * and the folder scan is a timer rather than a walk of the tree. The permission launcher, the
 * SAF contract and the resume check for `MANAGE_EXTERNAL_STORAGE` replace those two seams; the
 * phases and the copy around them do not move.
 */
class SourceAccessViewModel(
    key: Destination.Source.Access,
) : ViewModel() {

    private val _state = MutableStateFlow(SourceAccessState(kind = key.kind))
    val state: StateFlow<SourceAccessState> = _state.asStateFlow()

    private val effects = Channel<SourceAccessUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    private var scanJob: Job? = null

    fun onIntent(intent: SourceAccessIntent) {
        when (intent) {
            SourceAccessIntent.AccessRequested -> requestAccess()

            is SourceAccessIntent.AccessAnswered -> onAnswered(intent.access)

            // Cancelling the scan drops back to the explainer, not out of the branch: the
            // permission is still granted, only the walk was abandoned.
            SourceAccessIntent.ScanCancelled -> {
                scanJob?.cancel()
                _state.update { it.copy(phase = SourceAccessState.Phase.Explaining) }
            }
        }
    }

    private fun requestAccess() {
        if (_state.value.kind == SourceKindUi.WholeDevice) {
            viewModelScope.launch { effects.send(SourceAccessUiEffect.OpenSystemSettings) }
        }
        onAnswered(SourceAccessUi.Full)
    }

    private fun onAnswered(access: SourceAccessUi?) {
        if (access == null) {
            _state.update { it.copy(phase = SourceAccessState.Phase.Denied) }
            return
        }

        if (_state.value.kind != SourceKindUi.Folder) {
            viewModelScope.launch { effects.send(SourceAccessUiEffect.NavigateToMode(access)) }
            return
        }

        scanJob?.cancel()
        scanJob = viewModelScope.launch { scanFolder(access) }
    }

    /** Placeholder walk: the numbers are the ones the deck shows, paced to look like work. */
    private suspend fun scanFolder(access: SourceAccessUi) {
        _state.update {
            it.copy(
                phase = SourceAccessState.Phase.Scanning,
                scanProgress = 0f,
                scanPath = SampleFolderPath,
                scanSummary = "",
            )
        }

        repeat(ScanSteps) { step ->
            delay(ScanStepMillis)
            _state.update {
                it.copy(
                    scanProgress = (step + 1).toFloat() / ScanSteps,
                    scanSummary = SampleFolderSummary,
                )
            }
        }

        effects.send(SourceAccessUiEffect.NavigateToMode(access))
    }

    companion object {
        private const val ScanSteps = 10
        private const val ScanStepMillis = 120L

        const val SampleFolderPath = "/storage/emulated/0/DCIM/Projects"
        const val SampleFolderSummary = "842 files · 6.1 GB · 37 nested folders"
    }
}
