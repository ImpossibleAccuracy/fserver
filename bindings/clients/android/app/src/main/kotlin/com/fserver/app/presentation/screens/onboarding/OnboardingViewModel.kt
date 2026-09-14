package com.fserver.app.presentation.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.domain.AuthManager
import com.fserver.app.presentation.screens.onboarding.model.OnboardingIntent
import com.fserver.app.presentation.screens.onboarding.model.OnboardingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Three steps: why the app exists, where the bytes go, and what the app is for. Skippable at
 * every step — a user who already knows the product should reach the file list in one tap.
 */
class OnboardingViewModel(
    private val authManager: AuthManager,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun onIntent(intent: OnboardingIntent) {
        when (intent) {
            is OnboardingIntent.PageSettled ->
                _state.value = _state.value.copy(pageIndex = intent.index)
        }
    }

    fun finish() {
        viewModelScope.launch {
            authManager.ensureLoggedIn()
        }
    }
}
