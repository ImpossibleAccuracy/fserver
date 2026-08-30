package com.fserver.app.presentation.screens.source.pick

import com.fserver.app.presentation.screens.source.pick.model.SourcePickIntent
import com.fserver.app.presentation.screens.source.pick.model.SourcePickState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class SourcePickHandler(
    scope: CoroutineScope,
) {
    private val editable = MutableStateFlow(Editable())

    val state: StateFlow<SourcePickState> = editable
        .map { SourcePickState(moreExpanded = it.moreExpanded) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), SourcePickState())

    fun onIntent(intent: SourcePickIntent) {
        when (intent) {
            SourcePickIntent.MoreToggled ->
                editable.update { it.copy(moreExpanded = !it.moreExpanded) }
        }
    }

    private data class Editable(
        val moreExpanded: Boolean = false,
    )
}
