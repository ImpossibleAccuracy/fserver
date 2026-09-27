package com.fserver.app.presentation.screens.source.request

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.source.request.model.SyncRequestIntent
import com.fserver.app.presentation.screens.source.request.model.SyncRequestState
import com.fserver.app.presentation.screens.source.request.model.SyncRequestUiEffect
import com.fserver.app.presentation.screens.source.request.shared.model.toUi
import com.fserver.app.presentation.screens.source.shared.model.HostLocationUi
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.app.presentation.screens.source.shared.preferences.model.SourcePreferencesUi
import com.fserver.app.presentation.screens.source.shared.model.SourceRoleUi
import com.fserver.app.presentation.screens.source.shared.preferences.model.reduce
import com.fserver.app.presentation.screens.source.shared.model.toLocation
import com.fserver.app.presentation.screens.source.shared.preferences.model.toPreferences
import com.fserver.app.presentation.shared.error.ErrorReporter
import com.fserver.core.disk.DiskUsageRepository
import com.fserver.core.storage.TrustedDevicesRepository
import com.fserver.core.sync.SourcesController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SyncRequestViewModel(
    private val key: Destination.Source.Request.Details,
    private val sourcesController: SourcesController,
    private val trustedDevices: TrustedDevicesRepository,
    private val diskUsage: DiskUsageRepository,
    val reporter: ErrorReporter,
) : ViewModel() {

    private val editable = MutableStateFlow(Editable())

    private val sourceId = key.sourceId

    private val effects = Channel<SyncRequestUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    private val disk = diskUsage.usage
        .map<_, SyncRequestState.DiskUi?> {
            SyncRequestState.DiskUi(totalBytes = it.totalBytes, freeBytes = it.freeBytes)
        }
        .onStart { emit(null) }

    val state: StateFlow<SyncRequestState?> = combine(
        sourcesController.incomingRequests,
        trustedDevices.devices,
        disk,
        editable,
    ) { requests, devices, disk, local ->
        val request = requests.firstOrNull { it.sourceId == sourceId }?.toUi(devices)

        SyncRequestState(
            request = request,
            location = local.location,
            folder = local.folder,
            folderHasFiles = local.folderHasFiles,
            disk = disk,
            preferences = local.preferences
                ?: SourcePreferencesUi.build(request?.mode ?: SourceModeUi.Sync, SourceRoleUi.Follower),
            isAnswering = local.answering,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    fun onIntent(intent: SyncRequestIntent) {
        when (intent) {
            SyncRequestIntent.Declined -> decline()
            SyncRequestIntent.AppStorageSelected ->
                editable.update { it.copy(location = HostLocationUi.AppStorage) }

            is SyncRequestIntent.FolderPicked -> pickFolder(intent)
            SyncRequestIntent.FolderSelected ->
                editable.update { it.copy(location = it.folder ?: it.location) }

            is SyncRequestIntent.PreferencesChanged -> changePreferences(intent)

            SyncRequestIntent.Accepted -> accept()
        }
    }

    private fun changePreferences(intent: SyncRequestIntent.PreferencesChanged) {
        val current = state.value?.preferences ?: return
        editable.update { it.copy(preferences = current.reduce(intent.intent)) }
    }

    private fun pickFolder(intent: SyncRequestIntent.FolderPicked) {
        val folder = HostLocationUi.Folder(uri = intent.uri, label = intent.label)
        editable.update {
            it.copy(location = folder, folder = folder, folderHasFiles = intent.hasFiles)
        }
    }

    private fun decline() =
        answer(SyncRequestUiEffect.Declined, "Could not decline source $sourceId") {
            sourcesController.rejectRequest(sourceId)
        }

    private fun accept() {
        val current = state.value ?: return

        answer(SyncRequestUiEffect.Accepted(sourceId), "Could not accept source $sourceId") {
            sourcesController.acceptRequest(
                sourceId = sourceId,
                location = current.location.toLocation(sourceId),
                preferences = current.preferences.toPreferences(),
            )
        }
    }

    private fun answer(
        effect: SyncRequestUiEffect,
        failureContext: String,
        call: suspend () -> Result<Any?>,
    ) {
        if (editable.value.answering) return
        editable.update { it.copy(answering = true) }

        viewModelScope.launch {
            call().fold(
                onSuccess = { effects.send(effect) },
                onFailure = { reporter.report(it, failureContext) },
            )
            editable.update { it.copy(answering = false) }
        }
    }

    private data class Editable(
        val location: HostLocationUi = HostLocationUi.AppStorage,
        val folder: HostLocationUi.Folder? = null,
        val folderHasFiles: Boolean = false,
        val preferences: SourcePreferencesUi? = null,
        val answering: Boolean = false,
    )
}
