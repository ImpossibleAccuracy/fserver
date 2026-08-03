package com.fserver.app.presentation.screens.profile

import androidx.lifecycle.ViewModel
import com.fserver.app.data.DemoContentSource
import com.fserver.app.presentation.screens.profile.model.ServerProfileIntent
import com.fserver.app.presentation.screens.profile.model.ServerProfileState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the scanned code turned out to contain, before anything is applied.
 *
 * The profile is the server's claim, not settled fact: the user can still override it.
 * Note that the password lives in state deliberately un-persisted — it is not written to
 * `SavedStateHandle`, so it does not survive process death in plaintext.
 */
class ServerProfileViewModel(
    content: DemoContentSource,
) : ViewModel() {

    private val _state = MutableStateFlow(ServerProfileState(profile = content.scannedProfile()))
    val state: StateFlow<ServerProfileState> = _state.asStateFlow()

    fun onIntent(intent: ServerProfileIntent) {
        when (intent) {
            is ServerProfileIntent.PasswordChanged ->
                _state.value = _state.value.copy(password = intent.password)
        }
    }
}
