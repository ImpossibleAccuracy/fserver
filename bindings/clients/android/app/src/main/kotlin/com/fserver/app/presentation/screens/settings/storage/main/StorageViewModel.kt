package com.fserver.app.presentation.screens.settings.storage.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.composable.model.toUi
import com.fserver.app.presentation.composable.model.LinkDirectionUi
import com.fserver.app.presentation.screens.settings.storage.main.model.StorageIntent
import com.fserver.app.presentation.screens.settings.storage.main.model.StorageState
import com.fserver.app.presentation.screens.settings.storage.main.model.PeerUi
import com.fserver.app.presentation.screens.settings.storage.main.model.peerOf
import com.fserver.app.presentation.screens.settings.storage.main.model.peers
import com.fserver.app.presentation.composable.model.direction
import com.fserver.app.presentation.screens.settings.storage.main.model.StorageState.FreeableUi
import com.fserver.app.presentation.shared.browser.model.asPreviewFile
import com.fserver.core.disk.AppFootprint
import com.fserver.core.disk.DiskUsageRepository
import com.fserver.core.files.FilesController
import com.fserver.core.files.SyncFileEntry
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.storage.RegisteredSourcesRepository
import com.fserver.core.storage.TrustedDevicesRepository
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.model.SourceEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class StorageViewModel(
    diskUsage: DiskUsageRepository,
    registeredSources: RegisteredSourcesRepository,
    filesController: FilesController,
    trustedDevices: TrustedDevicesRepository,
    devicesRepository: DevicesRepository,
) : ViewModel() {
    val state: StateFlow<StorageState> = combine(
        diskUsage.usage,
        registeredSources.indexedSize,
        registeredSources.sources,
        filesController.overallContent,
        peers(trustedDevices, devicesRepository),
    ) { usage, indexed, sources, files, peers ->
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
            freeable = appData.filter { it.kind.isFreeable }.map(FreeableUi::AppData) +
                    sources.mapNotNull {
                        it.copiesUi(
                            here[it.id].orEmpty(),
                            peers.peerOf(it.deviceId).name
                        )
                    },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StorageState(),
    )

    fun onIntent(intent: StorageIntent) {
        when (intent) {
            is StorageIntent.FreeUpConfirmed -> freeUp(intent.keys)
        }
    }

    private fun freeUp(keys: Set<String>) {
        // TODO: free up the selected app data and evict the selected links' copies. Evict, never delete.
    }

    private fun appDataOf(footprint: AppFootprint): List<StorageState.AppDataUi> = listOf(
        // TODO: downloaded-from-devices, received files and conflict copies have no data source yet.
        StorageState.AppDataUi(StorageState.AppDataKindUi.Cache, footprint.cacheBytes),
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

private fun SourceEntry.copiesUi(here: List<SyncFileEntry>, deviceName: String): StorageState.FreeableUi? {
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

private const val PreviewCount = 5
