package com.fserver.app.presentation.screens.settings

import androidx.lifecycle.ViewModel
import com.fserver.app.data.DemoContentSource
import com.fserver.app.presentation.screens.settings.model.SettingsIntent
import com.fserver.app.presentation.screens.settings.model.SettingsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Settings.
 *
 * Storage encryption is reported, not toggled: the server owns the keys, so the client
 * cannot turn it on or off, and the screen says plainly what it does and does not protect
 * against. Presenting it as a client-side switch would be a lie about who enforces it.
 */
class SettingsViewModel(
    content: DemoContentSource,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsState(
            deviceCount = 5,
            downloadFolder = content.downloadFolder(),
        )
    )
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.WifiOnlyChanged ->
                _state.value = _state.value.copy(wifiOnly = intent.enabled)

            is SettingsIntent.CompressChanged ->
                _state.value = _state.value.copy(compressOnTheFly = intent.enabled)
        }
    }
}
