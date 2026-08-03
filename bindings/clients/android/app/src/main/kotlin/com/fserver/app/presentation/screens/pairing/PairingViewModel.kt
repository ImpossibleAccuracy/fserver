package com.fserver.app.presentation.screens.pairing

import androidx.lifecycle.ViewModel
import com.fserver.app.data.DemoContentSource
import com.fserver.app.presentation.screens.pairing.model.PairingIntent
import com.fserver.app.presentation.screens.pairing.model.PairingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Trust on first connection.
 *
 * The fingerprint comparison happens in the user's head, not here. That statement is the
 * whole security decision, so it is never inferred from anything else (a tap elsewhere, a
 * timeout, a remembered preference): only the explicit confirm button leaves this screen
 * connected.
 */
class PairingViewModel(
    deviceId: String,
    content: DemoContentSource,
) : ViewModel() {

    private val _state = MutableStateFlow(PairingState(candidate = content.pairingCandidate(deviceId)))
    val state: StateFlow<PairingState> = _state.asStateFlow()

    fun onIntent(intent: PairingIntent) {
        when (intent) {
            is PairingIntent.RememberDeviceChanged ->
                _state.value = _state.value.copy(rememberDevice = intent.remember)
        }
    }
}
