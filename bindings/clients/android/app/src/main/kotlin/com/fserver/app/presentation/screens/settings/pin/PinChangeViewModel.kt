package com.fserver.app.presentation.screens.settings.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.settings.pin.model.PIN_LENGTH
import com.fserver.app.presentation.screens.settings.pin.model.PinChangeIntent
import com.fserver.app.presentation.screens.settings.pin.model.PinChangeState
import com.fserver.app.presentation.screens.settings.pin.model.PinChangeUiEffect
import com.fserver.core.network.auth.AuthMethod
import com.fserver.core.storage.AuthSettingsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class PinChangeViewModel(
    private val key: Destination.Settings.PinChange,
    private val authSettings: AuthSettingsRepository,
) : ViewModel() {

    private var firstEntry: String? = null
    private var entry: String = ""

    private val _state = MutableStateFlow(PinChangeState())
    val state: StateFlow<PinChangeState> = _state.asStateFlow()

    private val effects = Channel<PinChangeUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    fun onIntent(intent: PinChangeIntent) {
        when (intent) {
            is PinChangeIntent.DigitPressed -> onDigit(intent.digit)
            PinChangeIntent.BackspacePressed -> onBackspace()
        }
    }

    private fun onDigit(digit: Char) {
        if (entry.length >= PIN_LENGTH) return
        entry += digit

        _state.value = _state.value.copy(filled = entry.length, isMismatch = false)
        if (entry.length == PIN_LENGTH) onComplete()
    }

    private fun onBackspace() {
        if (entry.isEmpty()) return
        entry = entry.dropLast(1)
        _state.value = _state.value.copy(filled = entry.length, isMismatch = false)
    }

    private fun onComplete() {
        val first = firstEntry

        when {
            first == null -> {
                firstEntry = entry
                entry = ""
                _state.value = PinChangeState(step = PinChangeState.Step.Repeat)
            }

            first != entry -> {
                firstEntry = null
                entry = ""
                _state.value = PinChangeState(step = PinChangeState.Step.New, isMismatch = true)
            }

            else -> onPinChosen(entry)
        }
    }

    private fun onPinChosen(pin: String) {
        firstEntry = null
        entry = ""
        viewModelScope.launch {
            authSettings.setServerPin(pin)
            if (key.enableOnSave) authSettings.setEnabled(AuthMethod.Pin, enabled = true)
            effects.send(PinChangeUiEffect.NavigateBack)
        }
    }
}
