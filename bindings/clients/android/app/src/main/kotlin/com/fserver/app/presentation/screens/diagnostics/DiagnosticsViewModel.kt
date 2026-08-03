package com.fserver.app.presentation.screens.diagnostics

import androidx.lifecycle.ViewModel
import com.fserver.app.data.DemoContentSource
import com.fserver.app.presentation.screens.diagnostics.model.DiagnosticsIntent
import com.fserver.app.presentation.screens.diagnostics.model.DiagnosticsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Connection diagnostics.
 *
 * The checks report facts with their consequence, not verdicts: a router dropping
 * multicast and a server one protocol version behind both come back as warnings on a
 * setup that works fine. Marking either as a failure would push users to "fix" a working
 * connection.
 */
class DiagnosticsViewModel(
    private val content: DemoContentSource,
) : ViewModel() {

    private val _state = MutableStateFlow(DiagnosticsState(checks = content.diagnosticChecks()))
    val state: StateFlow<DiagnosticsState> = _state.asStateFlow()

    fun onIntent(intent: DiagnosticsIntent) {
        when (intent) {
            DiagnosticsIntent.RecheckClicked ->
                _state.value = _state.value.copy(checks = content.diagnosticChecks())
        }
    }
}
