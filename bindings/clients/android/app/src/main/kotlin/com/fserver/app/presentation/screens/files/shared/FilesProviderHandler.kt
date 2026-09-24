package com.fserver.app.presentation.screens.files.shared

import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.shared.browser.model.toFlatPreview
import com.fserver.core.files.FilesController
import com.fserver.core.files.SyncFileEntry
import com.fserver.core.storage.RegisteredSourcesRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
class FilesProviderHandler(
    private val filesController: FilesController,
    private val registeredSourcesRepository: RegisteredSourcesRepository,
    private val openFile: (SyncFileEntry) -> Unit,
) {
    fun loadPreviewFiles(
        folder: String? = null,
        requiredLocation: FileBrowserUi.File.Location? = null,
        sourceIds: Set<String>? = null,
    ): Flow<List<FileBrowserUi.File>> = combine(
        filesController.overallContent.debounce(50.milliseconds),
        registeredSourcesRepository.sources,
    ) { entries, sources ->
        val sourcesByIds = sources.associateBy { it.id }

        // Two sources registered against the same directory would otherwise pour their files into
        // one folder: the label each was registered under keeps them apart.
        val sharedOrigins = sources
            .groupBy { it.originPath }
            .filterValues { it.size > 1 }
            .values
            .flatMapTo(mutableSetOf()) { group -> group.map { it.id } }

        entries
            .filter { sourceIds == null || it.sourceId in sourceIds }
            .map {
                val source = sourcesByIds[it.sourceId]!!
                val root = when (source.id) {
                    in sharedOrigins -> "${source.originPath}/${source.label}"
                    else -> source.originPath
                }

                it.copy(path = "$root/${it.path}")
            }
            .toFlatPreview(directory = folder ?: "/")
            .let { files ->
                if (requiredLocation == null) files
                else files.filter { it.location == requiredLocation }
            }
    }

    suspend fun onItemClick(entry: SyncFileEntry) {
        if (entry.locator == null) {
            // TODO: download path
        } else {
            openFile(entry)
        }
    }
}
