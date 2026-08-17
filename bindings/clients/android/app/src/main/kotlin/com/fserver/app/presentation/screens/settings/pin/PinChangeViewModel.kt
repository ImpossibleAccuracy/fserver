package com.fserver.app.presentation.screens.settings.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.screens.settings.pin.model.PIN_LENGTH
import com.fserver.app.presentation.screens.settings.pin.model.PinChangeIntent
import com.fserver.app.presentation.screens.settings.pin.model.PinChangeState
import com.fserver.app.presentation.screens.settings.pin.model.PinChangeUiEffect
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Entering a new PIN twice.
 *
 * TODO: nothing is stored. There is no PIN store and no launch-time lock screen yet, so the second
 *  entry is compared and then dropped — the flow is real, the outcome is not. The store call goes
 *  where [onPinChosen] is.
 */
class PinChangeViewModel : ViewModel() {

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
            // First pass: hold it and ask again. Nothing is decided yet.
            first == null -> {
                firstEntry = entry
                entry = ""
                _state.value = PinChangeState(step = PinChangeState.Step.Repeat)
            }

            // A mismatch restarts the whole thing rather than just the repeat: the user may have
            // mistyped the first entry, and there is no way to tell which one was wrong.
            first != entry -> {
                firstEntry = null
                entry = ""
                _state.value = PinChangeState(step = PinChangeState.Step.New, isMismatch = true)
            }

            else -> onPinChosen(entry)
        }
    }

    private fun onPinChosen(pin: String) {
        // TODO: persist `pin` once an app-lock store exists. It must not be stored in plain
        //  preferences — hash it with a per-install salt, next to the identity key pair.
        firstEntry = null
        entry = ""
        viewModelScope.launch { effects.send(PinChangeUiEffect.NavigateBack) }
    }
}
