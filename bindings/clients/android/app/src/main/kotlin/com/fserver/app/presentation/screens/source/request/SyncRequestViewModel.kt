package com.fserver.app.presentation.screens.source.request

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.domain.documents.isOwnDocument
import com.fserver.app.presentation.composable.model.peers
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.source.request.model.SyncRequestIntent
import com.fserver.app.presentation.screens.source.request.model.SyncRequestState
import com.fserver.app.presentation.screens.source.request.model.SyncRequestUiEffect
import com.fserver.app.presentation.screens.source.request.shared.model.toUi
import com.fserver.app.presentation.screens.source.shared.model.HostLocationUi
import com.fserver.app.presentation.screens.source.shared.model.SourceKindUi
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.app.presentation.screens.source.shared.model.SourceRoleUi
import com.fserver.app.presentation.screens.source.shared.model.readablePath
import com.fserver.app.presentation.screens.source.shared.model.toLocation
import com.fserver.app.presentation.screens.source.shared.preferences.model.SourcePreferencesUi
import com.fserver.app.presentation.screens.source.shared.preferences.model.reduce
import com.fserver.app.presentation.screens.source.shared.preferences.model.toPreferences
import com.fserver.app.presentation.screens.source.shared.preferences.model.withFloor
import com.fserver.app.presentation.screens.source.shared.preferences.model.withLocation
import com.fserver.app.presentation.shared.browser.FileBrowserNavigation
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.shared.browser.model.toPreview
import com.fserver.app.presentation.shared.error.AppError
import com.fserver.app.presentation.shared.error.ErrorReporter
import com.fserver.app.util.stateInScreen
import com.fserver.core.disk.DiskUsageRepository
import com.fserver.core.files.FilesController
import com.fserver.core.files.SourceLocation
import com.fserver.core.files.StorageVolumes
import com.fserver.core.files.scan.DirectoryScanProgress
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.storage.TrustedDevicesRepository
import com.fserver.core.sync.SourcesController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SyncRequestViewModel(
    private val key: Destination.Source.Request.Details,
    private val context: Context,
    private val filesController: FilesController,
    private val sourcesController: SourcesController,
    private val trustedDevices: TrustedDevicesRepository,
    devicesRepository: DevicesRepository,
    private val diskUsage: DiskUsageRepository,
    val reporter: ErrorReporter,
) : ViewModel() {

    private val editable = MutableStateFlow(Editable())

    private val sourceId = key.sourceId

    private var scanJob: Job? = null

    private val effects = Channel<SyncRequestUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    private val disk = diskUsage.usage
        .map<_, SyncRequestState.DiskUi?> {
            SyncRequestState.DiskUi(totalBytes = it.totalBytes, freeBytes = it.freeBytes)
        }
        .onStart { emit(null) }

    val state: StateFlow<SyncRequestState?> = combine(
        sourcesController.incomingRequests,
        devicesRepository.peers(),
        trustedDevices.devices,
        disk,
        editable,
    ) { requests, peers, trusted, disk, local ->
        val request = requests.firstOrNull { it.sourceId == sourceId }?.toUi(peers, trusted)

        SyncRequestState(
            request = request,
            location = local.location,
            folder = local.folder,
            directory = local.directory,
            picker = local.picker?.toUi(),
            disk = disk,
            preferences = (local.preferences ?: SourcePreferencesUi.build(
                mode = request?.mode ?: SourceModeUi.Sync,
                role = SourceRoleUi.Follower
            ))
                .withFloor(request?.files, request?.bytes)
                .withLocation(local.location.toLocation(sourceId)),
            isAnswering = local.answering,
        )
    }.stateInScreen(viewModelScope, null)

    fun onIntent(intent: SyncRequestIntent) {
        when (intent) {
            SyncRequestIntent.Declined -> decline()
            SyncRequestIntent.AppStorageSelected ->
                editable.update { it.copy(location = HostLocationUi.AppStorage) }

            is SyncRequestIntent.FolderPicked -> pickFolder(intent)
            SyncRequestIntent.FolderSelected ->
                editable.update { it.copy(location = it.folder ?: it.location) }

            is SyncRequestIntent.DeviceAccessAnswered -> answerDeviceAccess(intent.granted)

            SyncRequestIntent.DirectorySelected ->
                editable.update { it.copy(location = it.directory ?: it.location) }

            SyncRequestIntent.DirectoryConfirmed -> confirmDirectory()

            SyncRequestIntent.DirectoryPickCancelled -> cancelDirectoryPick()

            is SyncRequestIntent.PreferencesChanged -> changePreferences(intent)

            SyncRequestIntent.Accepted -> accept()
        }
    }

    private fun changePreferences(intent: SyncRequestIntent.PreferencesChanged) {
        val current = state.value?.preferences ?: return
        editable.update { it.copy(preferences = current.reduce(intent.intent)) }
    }

    private fun pickFolder(intent: SyncRequestIntent.FolderPicked) {
        if (SourceLocation.Tree(intent.uri).isOwnDocument) return reporter.report(AppError.OwnFolder)
        val folder = HostLocationUi.Folder(
            uri = intent.uri,
            label = intent.label,
            hasFiles = intent.hasFiles
        )
        editable.update { it.copy(location = folder, folder = folder) }
    }

    private fun answerDeviceAccess(granted: Boolean) {
        if (granted) scanDevice()
        else editable.update { it.copy(picker = Picker(phase = SyncRequestState.DirectoryPickerUi.Phase.Denied)) }
    }

    private fun scanDevice() {
        scanJob?.cancel()
        editable.update { it.copy(picker = Picker()) }

        scanJob = viewModelScope.launch {
            try {
                val root = StorageVolumes.fromContext(context)
                val task = filesController.loadContent(directory = root)

                task.progress.collect { progress -> updatePicker { it.copy(progress = progress) } }

                val tree =
                    task.result().getOrThrow().toPreview(SourceKindUi.WholeDevice, root.volumes)
                updatePicker {
                    it.copy(
                        phase = SyncRequestState.DirectoryPickerUi.Phase.Browsing,
                        preview = tree
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reporter.report(e, "Could not read the device for a host folder")
                updatePicker { it.copy(phase = SyncRequestState.DirectoryPickerUi.Phase.Failed) }
            }
        }
    }

    private fun confirmDirectory() {
        val opened = editable.value.picker?.opened ?: return
        val directory = HostLocationUi.Directory(
            path = opened.path,
            label = SourceLocation.Directory(opened.path).readablePath() ?: opened.path,
            hasFiles = opened.files > 0,
        )

        editable.update { it.copy(location = directory, directory = directory, picker = null) }
    }

    private fun cancelDirectoryPick() {
        scanJob?.cancel()
        editable.update { it.copy(picker = null) }
    }

    private fun updatePicker(transform: (Picker) -> Picker) =
        editable.update { it.copy(picker = it.picker?.let(transform)) }

    private fun Picker.toUi() = SyncRequestState.DirectoryPickerUi(
        phase = phase,
        progress = progress,
        preview = preview,
        navigation = if (phase == SyncRequestState.DirectoryPickerUi.Phase.Browsing) {
            FileBrowserNavigation(
                opened = opened,
                onOpen = { entry -> updatePicker { it.copy(opened = entry) } },
                onUp = {
                    updatePicker {
                        it.copy(
                            opened = (it.preview as? FileBrowserUi.Tree)?.parentOf(
                                it.opened
                            )
                        )
                    }
                },
            )
        } else {
            null
        },
    )

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
        val directory: HostLocationUi.Directory? = null,
        val picker: Picker? = null,
        val preferences: SourcePreferencesUi? = null,
        val answering: Boolean = false,
    )

    private data class Picker(
        val phase: SyncRequestState.DirectoryPickerUi.Phase = SyncRequestState.DirectoryPickerUi.Phase.Scanning,
        val progress: DirectoryScanProgress? = null,
        val preview: FileBrowserUi? = null,
        val opened: FileBrowserUi.Directory? = null,
    )
}
