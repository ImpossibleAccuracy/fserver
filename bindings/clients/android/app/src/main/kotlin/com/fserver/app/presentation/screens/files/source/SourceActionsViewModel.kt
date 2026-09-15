package com.fserver.app.presentation.screens.files.source

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.error.ErrorReporter
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.files.source.model.SourceActionsIntent
import com.fserver.app.presentation.screens.files.source.model.SourceActionsState
import com.fserver.app.presentation.screens.files.source.model.SourceActionsUiEffect
import com.fserver.app.presentation.screens.source.shared.model.toUi
import com.fserver.core.storage.RegisteredSourcesRepository
import com.fserver.core.sync.SourcesController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SourceActionsViewModel(
    private val key: Destination.Files.SourceActions,
    private val registeredSources: RegisteredSourcesRepository,
    private val sourcesController: SourcesController,
    private val reporter: ErrorReporter,
) : ViewModel() {
    private val effects = Channel<SourceActionsUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    private val editable = MutableStateFlow(Editable())

    val state: StateFlow<SourceActionsState> = combine(
        registeredSources.observeById(key.sourceId),
        editable,
    ) { source, edit ->
        SourceActionsState(
            name = edit.name ?: source?.label.orEmpty(),
            originalName = source?.label.orEmpty(),
            mode = source?.syncMode?.toUi(),
            isBusy = edit.isBusy,
            confirmingRemoval = edit.confirmingRemoval,
            isMissing = source == null,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SourceActionsState(),
    )

    fun onIntent(intent: SourceActionsIntent) {
        when (intent) {
            is SourceActionsIntent.NameChanged -> editable.update { it.copy(name = intent.name) }

            SourceActionsIntent.SaveClicked -> rename()

            SourceActionsIntent.RemoveClicked -> editable.update {
                it.copy(confirmingRemoval = true)
            }

            SourceActionsIntent.RemoveCancelled -> editable.update {
                it.copy(confirmingRemoval = false)
            }

            SourceActionsIntent.RemoveConfirmed -> remove()
        }
    }

    private fun rename() {
        val name = state.value.name.trim()
        if (!state.value.canSave) return

        editable.update { it.copy(isBusy = true) }

        viewModelScope.launch {
            runCatching { registeredSources.rename(key.sourceId, name) }
                .fold(
                    onSuccess = { effects.send(SourceActionsUiEffect.Dismiss) },
                    onFailure = { failure ->
                        editable.update { it.copy(isBusy = false) }
                        reporter.report(failure, "Could not rename source ${key.sourceId}")
                    },
                )
        }
    }

    private fun remove() {
        if (state.value.isBusy) return
        editable.update { it.copy(isBusy = true) }

        viewModelScope.launch {
            sourcesController.removeSource(key.sourceId)
                .fold(
                    onSuccess = { effects.send(SourceActionsUiEffect.Dismiss) },
                    onFailure = { failure ->
                        editable.update { it.copy(isBusy = false, confirmingRemoval = false) }
                        reporter.report(failure, "Could not remove source ${key.sourceId}")
                    },
                )
        }
    }

    private data class Editable(
        val name: String? = null,
        val isBusy: Boolean = false,
        val confirmingRemoval: Boolean = false,
    )
}
