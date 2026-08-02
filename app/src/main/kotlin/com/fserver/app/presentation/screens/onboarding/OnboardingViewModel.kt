package com.fserver.app.presentation.screens.onboarding

import androidx.lifecycle.ViewModel
import com.fserver.app.presentation.screens.onboarding.model.OnboardingIntent
import com.fserver.app.presentation.screens.onboarding.model.OnboardingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Three steps: why the app exists, the network permission it needs, how to reach a
 * server. Skippable at every step — a user who already knows the product should reach
 * discovery in one tap.
 */
class OnboardingViewModel : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun onIntent(intent: OnboardingIntent) {
        when (intent) {
            is OnboardingIntent.PageSettled ->
                _state.value = _state.value.copy(pageIndex = intent.index)
        }
    }
}
