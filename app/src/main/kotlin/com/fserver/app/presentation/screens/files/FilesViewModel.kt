package com.fserver.app.presentation.screens.files

import androidx.lifecycle.ViewModel
import com.fserver.app.data.DemoContentSource
import com.fserver.app.presentation.screens.files.model.FilesIntent
import com.fserver.app.presentation.screens.files.model.FilesState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The server's tree, in whichever of the three shapes the user picked.
 *
 * The list is the default: it is the only view showing availability and size at a glance.
 * The grid serves photo and video folders; the tree keeps the whole structure on screen at
 * the cost of a narrow touch target, so it stays opt-in.
 */
class FilesViewModel(
    private val content: DemoContentSource,
) : ViewModel() {

    private val _state = MutableStateFlow(
        FilesState(
            serverName = content.serverName(),
            breadcrumb = content.breadcrumb(),
            files = content.files(),
            gridTiles = content.gridTiles(),
            tree = content.tree(),
            itemCount = content.itemCount(),
        )
    )
    val state: StateFlow<FilesState> = _state.asStateFlow()

    fun onIntent(intent: FilesIntent) {
        when (intent) {
            is FilesIntent.ViewModeSelected ->
                _state.value = _state.value.copy(viewMode = intent.mode)

            // Tapping a remote file will queue a download once :core is wired in; folders
            // will descend. The system picker and search are not built yet either.
            is FilesIntent.FileClicked -> Unit
            FilesIntent.SendFileClicked -> Unit
            FilesIntent.SearchClicked -> Unit

            FilesIntent.IncomingDemoRequested ->
                _state.value = _state.value.copy(incomingRequest = content.incomingRequest())

            FilesIntent.IncomingRequestDismissed ->
                _state.value = _state.value.copy(incomingRequest = null)
        }
    }
}
