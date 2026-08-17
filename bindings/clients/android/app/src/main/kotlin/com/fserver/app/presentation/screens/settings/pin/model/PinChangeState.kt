package com.fserver.app.presentation.screens.settings.pin.model

const val PIN_LENGTH = 6

/**
 * [filled] rather than the digits themselves: the screen only ever needs to know how many dots to
 * light, and the PIN has no reason to travel out of the ViewModel.
 */
data class PinChangeState(
    val step: Step = Step.New,
    val filled: Int = 0,
    val isMismatch: Boolean = false,
) {
    enum class Step { New, Repeat }
}
