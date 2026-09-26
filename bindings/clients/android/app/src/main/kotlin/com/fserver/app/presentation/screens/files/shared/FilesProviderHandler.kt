package com.fserver.app.presentation.screens.files.shared

import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.shared.browser.model.locations
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
    private val openFile: suspend (SyncFileEntry) -> Unit,
) {
    /**
     * Indexed entries matching the filters, with paths rooted at their source's origin.
     *
     * @param rooted Whether to root the paths at their source's origin, or leave them relative to it.
     */
    fun loadEntries(
        requiredLocation: FileBrowserUi.File.Location? = null,
        sourceIds: Set<String>? = null,
        rooted: Boolean = true,
    ): Flow<List<SyncFileEntry>> = combine(
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

        // Filtered before the tree is built, so a folder is listed only while something in it matches.
        entries
            .filter { sourceIds == null || it.sourceId in sourceIds }
            .filter { requiredLocation == null || requiredLocation in it.locations }
            .map {
                if (!rooted) return@map it

                val source = sourcesByIds[it.sourceId]!!
                val root = when (source.id) {
                    in sharedOrigins -> "${source.originPath}/${source.label}"
                    else -> source.originPath
                }

                it.copy(path = "$root/${it.path}")
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
