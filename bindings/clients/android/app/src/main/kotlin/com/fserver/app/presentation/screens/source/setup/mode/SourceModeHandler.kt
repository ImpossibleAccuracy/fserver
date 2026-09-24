package com.fserver.app.presentation.screens.source.setup.mode

import com.fserver.app.presentation.screens.source.setup.mode.model.SourceModeIntent
import com.fserver.app.presentation.screens.source.setup.mode.model.SourceModeState
import com.fserver.app.presentation.screens.source.setup.shared.model.SourceAccessUi
import com.fserver.app.presentation.screens.source.setup.shared.model.SourceSetupState
import com.fserver.app.presentation.screens.source.shared.model.SourceKindUi
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.app.presentation.screens.source.shared.model.toUi
import com.fserver.core.sync.SourcesController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class SourceModeHandler(
    private val sourcesController: SourcesController,

    private val flow: MutableStateFlow<SourceSetupState>,
    scope: CoroutineScope,
) {
    private val editable = MutableStateFlow(Editable())

    val state: StateFlow<SourceModeState?> = combine(flow, editable) { shared, local ->
        val kind = shared.kind ?: return@combine null
        val source = shared.source ?: return@combine null
        val modes = sourcesController.availableModes(source.location).map { it.toUi() }

        SourceModeState(
            kind = kind,
            modes = modes,
            selected = local.selected ?: shared.mode ?: modes.firstOrNull(),
            accessType = when (shared.kind) {
                SourceKindUi.Media ->
                    if (shared.access == SourceAccessUi.Partial)
                        SourceModeState.AccessType.Partial(source.files)
                    else null

                SourceKindUi.Folder,
                SourceKindUi.WholeDevice,
                SourceKindUi.AppStorage ->
                    SourceModeState.AccessType.Full(
                        label = source.label,
                        files = source.files,
                        size = source.bytes,
                    )
            },
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    fun onIntent(intent: SourceModeIntent) {
        when (intent) {
            is SourceModeIntent.ModeSelected ->
                editable.update { it.copy(selected = intent.mode) }

            SourceModeIntent.Confirmed -> commit()
        }
    }

    fun reset() {
        editable.value = Editable()
    }

    private fun commit() {
        val selected = state.value?.selected ?: return
        flow.update { it.copy(mode = selected) }
    }

    private data class Editable(
        val selected: SourceModeUi? = null,
    )
}
