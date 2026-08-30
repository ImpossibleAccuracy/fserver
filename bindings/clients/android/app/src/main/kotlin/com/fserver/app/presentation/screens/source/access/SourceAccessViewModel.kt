package com.fserver.app.presentation.screens.source.access

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.source.access.model.SourceAccessIntent
import com.fserver.app.presentation.screens.source.access.model.SourceAccessState
import com.fserver.app.presentation.screens.source.access.model.SourceAccessUiEffect
import com.fserver.app.presentation.screens.source.shared.SourceAccessGrant
import com.fserver.app.presentation.screens.source.shared.SourceAccessUi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
            is SourceAccessIntent.AccessAnswered -> when (intent.grant) {
                SourceAccessGrant.Denied -> deny()
                else -> startScan(intent.grant)
            }

            SourceAccessIntent.ScanCancelled -> {
                scanJob?.cancel()
                _state.update { it.copy(phase = SourceAccessState.Phase.Explaining) }
            }
        }
    }

    private fun deny() {
        _state.update { it.copy(phase = SourceAccessState.Phase.Denied) }
    }

    private fun continueWith(access: SourceAccessUi) {
        viewModelScope.launch { effects.send(SourceAccessUiEffect.NavigateToMode(access)) }
    }

    private fun startScan(grant: SourceAccessGrant) {
        scanJob?.cancel()
        _state.update {
            it.copy(
                phase = SourceAccessState.Phase.Scanning,
                scanPath = when (grant) {
                    SourceAccessGrant.Denied -> ""
                    SourceAccessGrant.AllFiles -> "All files"

                    is SourceAccessGrant.Media -> when (grant.access) {
                        SourceAccessUi.Full -> "All media"
                        SourceAccessUi.Partial -> "Partial media"
                    }

                    is SourceAccessGrant.Tree -> grant.label
                },
                scannedFiles = 0,
                scannedBytes = 0,
            )
        }

        /*scanJob = viewModelScope.launch {
            directoryScanner
                .scan(FoundDirectory.Path(grant.uri.toString()))
                .catch { error ->
                    Timber.e(error, "Failed to scan %s", grant.uri)
                    deny()
                }
                .collect { onScanState(it) }
        }*/
    }

    /*private fun onScanState(scan: DirectoryScanner.State) {
        when (scan) {
            is DirectoryScanner.State.Progress -> _state.update {
                it.copy(
                    scannedFiles = scan.scannedFiles,
                    scannedBytes = scan.scannedSize.bytes,
                )
            }

            is DirectoryScanner.State.Ready -> {
                _state.update {
                    it.copy(
                        scannedFiles = scan.files.size,
                        scannedBytes = scan.files.sumOf { file -> file.size.bytes },
                    )
                }
                continueWith(SourceAccessUi.Full)
            }
        }
    }*/
}
