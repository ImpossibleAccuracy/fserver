package com.fserver.app.presentation.screens.source.pick

import androidx.lifecycle.ViewModel
import com.fserver.app.presentation.screens.source.pick.model.SourcePickIntent
import com.fserver.app.presentation.screens.source.pick.model.SourcePickState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Asks one question and holds nothing else: which source the user picked travels in the
 * destination key, not in this ViewModel.
 */
class SourcePickViewModel : ViewModel() {

    private val _state = MutableStateFlow(SourcePickState())
    val state: StateFlow<SourcePickState> = _state.asStateFlow()

    fun onIntent(intent: SourcePickIntent) {
        when (intent) {
            SourcePickIntent.MoreToggled ->
                _state.update { it.copy(moreExpanded = !it.moreExpanded) }
        }
    }
}
