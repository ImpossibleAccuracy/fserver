package com.fserver.app.presentation.screens.files.shared

import com.fserver.app.presentation.screens.source.shared.preview.model.SourcePreviewUi
import com.fserver.app.presentation.screens.source.shared.preview.model.toFlatPreview
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
        requiredLocation: SourcePreviewUi.File.Location? = null,
        sourceIds: Set<String>? = null,
    ): Flow<List<SourcePreviewUi.File>> = combine(
        filesController.overallContent.debounce(50.milliseconds),
        registeredSourcesRepository.sources,
    ) { entries, sources ->
        val sourcesByIds = sources.associateBy { it.id }

        entries
            .filter { sourceIds == null || it.sourceId in sourceIds }
            .map {
                val source = sourcesByIds[it.sourceId]!!
                it.copy(
                    path = "${source.originPath}/${it.path}"
                )
            }
            // TODO: resolve conflicts between sources with same origin path
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
