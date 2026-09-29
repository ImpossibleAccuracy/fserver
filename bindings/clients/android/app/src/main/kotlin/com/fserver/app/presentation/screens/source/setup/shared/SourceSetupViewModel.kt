package com.fserver.app.presentation.screens.source.setup.shared

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.screens.source.setup.access.SourceAccessHandler
import com.fserver.app.presentation.screens.source.setup.conditions.SourceConditionsHandler
import com.fserver.app.presentation.screens.source.setup.done.SourceDoneHandler
import com.fserver.app.presentation.screens.source.setup.mode.SourceModeHandler
import com.fserver.app.presentation.screens.source.setup.pick.SourcePickHandler
import com.fserver.app.presentation.screens.source.setup.progress.SourceProgressHandler
import com.fserver.app.presentation.screens.source.setup.shared.model.SourceSetupState
import com.fserver.app.presentation.screens.source.shared.model.SourceKindUi
import com.fserver.app.presentation.shared.error.ErrorReporter
import com.fserver.core.files.FilesController
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.requirement.RequirementsChecker
import com.fserver.core.storage.RegisteredSourcesRepository
import com.fserver.core.sync.SourcesController
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
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
    private val sourcesRepository: RegisteredSourcesRepository,
    private val requirementsChecker: RequirementsChecker,
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

    val pick = SourcePickHandler(
        context = context,
        requirementsChecker = requirementsChecker,
        reporter = reporter,
        scope = viewModelScope,
    )

    val access = SourceAccessHandler(
        context = context,
        filesController = filesController,
        flow = editable,
        scope = viewModelScope,
        reporter = reporter,
    )

    val mode = SourceModeHandler(
        sourcesController = sourcesController,
        flow = editable,
        scope = viewModelScope,
    )

    val conditions = SourceConditionsHandler(
        devicesRepository = devicesRepository,
        sourcesController = sourcesController,
        register = ::register,
        flow = editable,
        scope = viewModelScope,
        reporter = reporter,
    )

    val progress = SourceProgressHandler(
        sourcesRepository = sourcesRepository,
        sourcesController = sourcesController,
        devicesRepository = devicesRepository,
        reporter = reporter,
        flow = editable,
        scope = viewModelScope,
    )

    val done = SourceDoneHandler(
        sourcesRepository = sourcesRepository,
        devicesRepository = devicesRepository,
        flow = editable,
        scope = viewModelScope,
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

    private suspend fun register(
        syncMode: SyncMode,
        preferences: SourceEntry.Preferences,
    ): Result<SourceEntry> {
        val shared = editable.value
        val source = shared.source ?: return Result.failure(SourceSetupIncompleteException())
        val deviceId = shared.targetDeviceId ?: return Result.failure(SourceSetupIncompleteException())

        return sourcesController.addSource(
            location = source.location,
            syncMode = syncMode,
            deviceId = deviceId,
            label = source.label.ifEmpty { shared.kind?.name.orEmpty() },
            preferences = preferences,
        )
    }

}
