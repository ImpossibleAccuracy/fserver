package com.fserver.app.presentation.screens.source.setup.shared

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.screens.source.setup.access.SourceAccessHandler
import com.fserver.app.presentation.screens.source.setup.conditions.SourceConditionsHandler
import com.fserver.app.presentation.screens.source.setup.mode.SourceModeHandler
import com.fserver.app.presentation.screens.source.setup.pick.SourcePickHandler
import com.fserver.app.presentation.screens.source.setup.shared.model.SourceSetupState
import com.fserver.app.presentation.screens.source.shared.model.SourceKindUi
import com.fserver.app.presentation.shared.error.ErrorReporter
import com.fserver.core.files.FilesController
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.sync.SourcesController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class SourceSetupViewModel(
    private val context: Context,
    private val filesController: FilesController,
    private val devicesRepository: DevicesRepository,
    private val sourcesController: SourcesController,
    private val reporter: ErrorReporter,
) : ViewModel() {

    private val editable = MutableStateFlow(SourceSetupState())

    val state: StateFlow<SourceSetupState> = editable.asStateFlow()

    val isStarted: StateFlow<Boolean> = editable
        .map { it.kind != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = editable.value.kind != null,
        )

    val pick = SourcePickHandler(viewModelScope)

    val access = SourceAccessHandler(
        context = context,
        filesController = filesController,
        flow = editable,
        scope = viewModelScope,
        reporter = reporter,
    )

    val mode = SourceModeHandler(editable, viewModelScope)

    val conditions = SourceConditionsHandler(
        devicesRepository = devicesRepository,
        sourcesController = sourcesController,
        flow = editable,
        scope = viewModelScope,
        reporter = reporter,
    )

    fun start(kind: SourceKindUi, targetDeviceId: String? = null) {
        access.reset()
        mode.reset()
        conditions.reset()
        editable.value = SourceSetupState(kind = kind, targetDeviceId = targetDeviceId)
    }

    /** Where the source goes, as the connect screen handed it back. */
    fun selectTarget(deviceId: String) {
        editable.update { it.copy(targetDeviceId = deviceId) }
    }

}
