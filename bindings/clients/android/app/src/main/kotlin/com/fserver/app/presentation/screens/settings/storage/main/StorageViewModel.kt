package com.fserver.app.presentation.screens.settings.storage.main

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.data.export.ArchiveExporter
import com.fserver.app.presentation.composable.model.LinkDirectionUi
import com.fserver.app.presentation.composable.model.PeerUi
import com.fserver.app.presentation.composable.model.direction
import com.fserver.app.presentation.composable.model.peerOf
import com.fserver.app.presentation.composable.model.peers
import com.fserver.app.presentation.composable.model.toUi
import com.fserver.app.presentation.screens.settings.storage.main.model.StorageIntent
import com.fserver.app.presentation.screens.settings.storage.main.model.StorageState
import com.fserver.app.presentation.screens.settings.storage.main.model.StorageUiEffect
import com.fserver.app.presentation.shared.browser.model.asPreviewFile
import com.fserver.app.util.combineMany
import com.fserver.app.util.stateInScreen
import com.fserver.core.disk.AppFootprint
import com.fserver.core.disk.DiskUsageRepository
import com.fserver.core.external.export.ExportProgress
import com.fserver.core.files.FilesController
import com.fserver.core.files.SyncFileEntry
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.storage.RegisteredSourcesRepository
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.model.SourceEntry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class StorageViewModel(
    private val diskUsage: DiskUsageRepository,
    private val archiveExporter: ArchiveExporter,
    registeredSources: RegisteredSourcesRepository,
    filesController: FilesController,
    devicesRepository: DevicesRepository,
) : ViewModel() {
    private val exporting = MutableStateFlow<StorageState.ExportUi?>(null)
    private val effects = Channel<StorageUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    val state: StateFlow<StorageState?> = combineMany(
        diskUsage.usage,
        registeredSources.indexedSize,
        registeredSources.sources,
        filesController.overallContent,
        devicesRepository.peers(),
        exporting,
    ) { usage, indexed, sources, files, peers, export ->
        val here = files
            .filter { it.localState is LocalIndexedFile.State.Present }
            .groupBy { it.sourceId }
        val appData = appDataOf(usage.footprint)

        StorageState(
            isLoading = false,
            usage = usage.toUi(indexedBytes = indexed.bytes),
            links = sources
                .map { it.toLinkUi(here[it.id].orEmpty(), peers.peerOf(it.deviceId)) }
                .sortedByDescending { it.bytes },
            appData = appData,
            freeable = appData.filter { it.kind.isFreeable }
                .map(StorageState.FreeableUi::AppData)
                .plus(
                    sources.mapNotNull {
                        it.copiesUi(
                            here[it.id].orEmpty(),
                            peers.peerOf(it.deviceId).name
                        )
                    }
                ),
            export = export,
        )
    }.stateInScreen(viewModelScope, null)

    fun onIntent(intent: StorageIntent) {
        when (intent) {
            is StorageIntent.FreeUpConfirmed -> freeUp(intent.keys)
            is StorageIntent.Export -> export(intent.uri)
        }
    }

    private fun freeUp(keys: Set<String>) {
        if (StorageState.FreeableUi.AppData.keyOf(StorageState.AppDataKindUi.EvictionPreviews) in keys) {
            viewModelScope.launch { diskUsage.clearEvictionPreviews() }
        }
        // TODO: free up the rest of the selected app data and evict the selected links' copies. Evict, never delete.
    }

    private fun export(uri: Uri) {
        if (exporting.value != null) return
        exporting.value = StorageState.ExportUi()

        viewModelScope.launch {
            // TODO: move to a worker / foreground service, so the export survives leaving the screen.
            val task = archiveExporter.export(uri)
            task.progress.collect { exporting.value = it.toUi() }
            val effect = task.result().fold(
                onSuccess = {
                    StorageUiEffect.ExportFinished(
                        files = it.files,
                        skipped = it.skipped.size
                    )
                },
                onFailure = {
                    Timber.w(it, "Export failed")
                    StorageUiEffect.ExportFailed
                },
            )
            exporting.value = null
            effects.send(effect)
        }
    }

    private fun appDataOf(footprint: AppFootprint): List<StorageState.AppDataUi> = listOf(
        // TODO: downloaded-from-devices and received files have no data source yet.
        StorageState.AppDataUi(StorageState.AppDataKindUi.Cache, footprint.cacheBytes),
        StorageState.AppDataUi(
            StorageState.AppDataKindUi.EvictionPreviews,
            footprint.evictionPreviewBytes
        ),
        StorageState.AppDataUi(StorageState.AppDataKindUi.Incomplete, footprint.stagingBytes),
    ).filter { it.bytes > 0 }
}

private fun SourceEntry.toLinkUi(here: List<SyncFileEntry>, peer: PeerUi) =
    StorageState.LinkUi(
        id = id,
        label = label,
        peer = peer,
        direction = direction(),
        files = here.size,
        bytes = here.sumOf { it.size.bytes },
    )

private fun SourceEntry.copiesUi(
    here: List<SyncFileEntry>,
    deviceName: String
): StorageState.FreeableUi? {
    if (direction() != LinkDirectionUi.Outgoing) return null

    val copied = here.filter { it.remoteState is LocalIndexedFile.State.Present }
    if (copied.isEmpty()) return null

    return StorageState.FreeableUi.LinkCopies(
        sourceId = id,
        label = label,
        deviceName = deviceName,
        files = copied.size,
        bytes = copied.sumOf { it.size.bytes },
        previews = copied
            .sortedByDescending { it.modifiedAt }
            .take(PreviewCount)
            .map { it.asPreviewFile() },
    )
}

private fun ExportProgress.toUi() = StorageState.ExportUi(
    files = files,
    totalFiles = totalFiles,
    writtenBytes = written.bytes,
    totalBytes = totalSize.bytes,
)

private const val PreviewCount = 5
