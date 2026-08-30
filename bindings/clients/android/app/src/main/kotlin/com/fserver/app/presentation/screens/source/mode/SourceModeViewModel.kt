package com.fserver.app.presentation.screens.source.mode

import androidx.lifecycle.ViewModel
import com.fserver.app.presentation.screens.source.shared.SourceAccessUi
import com.fserver.app.presentation.screens.source.shared.SourceKindUi
import com.fserver.app.presentation.screens.source.shared.modes
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.source.mode.model.SourceModeIntent
import com.fserver.app.presentation.screens.source.mode.model.SourceModeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * TODO: the source header ("842 files · 6.1 GB") is a fixture. It comes from the scan the
 * access step ran, which will hand it over through `:core` rather than through the key.
 */
class SourceModeViewModel(
    key: Destination.Source.Mode,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SourceModeState(
            kind = key.kind,
            access = key.access,
            selected = key.kind.modes.firstOrNull(),
            grantedItemCount = if (key.access == SourceAccessUi.Partial) SamplePartialCount else 0,
            sourceLabel = key.kind.sampleLabel(),
            sourceDetail = key.kind.sampleDetail(),
        )
    )
    val state: StateFlow<SourceModeState> = _state.asStateFlow()

    fun onIntent(intent: SourceModeIntent) {
        when (intent) {
            is SourceModeIntent.ModeSelected -> _state.update { it.copy(selected = intent.mode) }

            // TODO: re-raise the photo picker; a wider grant only changes the count above.
            SourceModeIntent.ChangeSelectionClicked -> Unit
        }
    }

    private companion object {
        const val SamplePartialCount = 34

        fun SourceKindUi.sampleLabel(): String = when (this) {
            SourceKindUi.Photos -> ""
            SourceKindUi.Folder -> "DCIM/Projects"
            SourceKindUi.WholeDevice -> "Whole device"
        }

        fun SourceKindUi.sampleDetail(): String = when (this) {
            SourceKindUi.Photos -> ""
            SourceKindUi.Folder -> "842 files · 6.1 GB"
            SourceKindUi.WholeDevice -> "18,402 files · 94.7 GB · access granted"
        }
    }
}
