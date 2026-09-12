package com.fserver.app.presentation.screens.files.shared

import com.fserver.app.presentation.screens.source.shared.preview.model.SourcePreviewUi
import com.fserver.app.presentation.screens.source.shared.preview.model.toFlatPreview
import com.fserver.core.files.FilesController
import com.fserver.core.files.SyncFileEntry
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
class FilesProviderHandler(
    private val filesController: FilesController,
    private val openFile: (SyncFileEntry) -> Unit,
) {
    fun loadPreviewFiles(
        folder: String? = null,
        requiredLocation: SourcePreviewUi.File.Location? = null,
    ): Flow<List<SourcePreviewUi.File>> = filesController.overallContent
        .debounce(50.milliseconds)
        .map { entries ->
            entries
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
