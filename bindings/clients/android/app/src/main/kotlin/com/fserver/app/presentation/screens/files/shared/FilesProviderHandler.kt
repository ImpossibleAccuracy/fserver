package com.fserver.app.presentation.screens.files.shared

import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.shared.browser.model.locations
import com.fserver.app.presentation.screens.source.shared.model.initiatorHalfOf
import com.fserver.app.presentation.shared.error.ErrorReporter
import com.fserver.core.files.FilesController
import com.fserver.core.files.SyncFileEntry
import com.fserver.core.storage.RegisteredSourcesRepository
import com.fserver.core.sync.metadata.PeerSourceMetadata
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.progress.FileTransfer
import com.fserver.core.sync.progress.SyncProgressRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
class FilesProviderHandler(
    private val filesController: FilesController,
    private val registeredSourcesRepository: RegisteredSourcesRepository,
    progress: SyncProgressRepository,
    private val reporter: ErrorReporter,
    private val openFile: suspend (SyncFileEntry) -> Unit,
) {
    private val downloading = MutableStateFlow<Set<FileKey>>(emptySet())
    private val failed = MutableStateFlow<Set<FileKey>>(emptySet())

    /**
     * How fetches from the peer are getting on: asked here by a tap, or by another app through
     * the documents provider - the engine reports both as incoming transfers.
     */
    val fetches: Flow<FileFetches> = combine(downloading, failed, progress.transfers) { asked, failed, transfers ->
        val receiving = transfers
            .filter { it.key.direction == FileTransfer.Direction.Incoming && !it.isFinished }
            .associate { FileKey(it.key.sourceId, it.key.fileId) to it.progress }

        FileFetches(receiving = receiving, asked = asked, failed = failed)
    }

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
        registeredSourcesRepository.metadata,
    ) { entries, sources, metadata ->
        val sourcesByIds = sources.associateBy { it.id }
        val origins = sources.associate { it.id to it.originRoot(metadata) }

        // Two sources registered against the same directory would otherwise pour their files into
        // one folder: the label each was registered under keeps them apart.
        val sharedOrigins = sources
            .groupBy { origins.getValue(it.id) }
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
                val origin = origins.getValue(source.id)
                val root = when (source.id) {
                    in sharedOrigins -> "$origin/${source.label}"
                    else -> origin
                }

                it.copy(path = "$root/${it.path}")
            }
    }

    /** Opens [entry], fetching it from the peer first when only the peer holds it. */
    suspend fun onItemClick(entry: SyncFileEntry) {
        if (!entry.isRemote) return openFile(entry)

        val key = FileKey(entry.sourceId, entry.fileId)
        // A second tap while the first fetch runs must not start another transfer of the same file.
        if (key in downloading.getAndUpdate { it + key }) return
        failed.update { it - key }

        try {
            filesController.download(entry)
                .onSuccess { openFile(entry.copy(locator = it.locator, localState = it.localState)) }
                .onFailure {
                    failed.update { failed -> failed + key }
                    reporter.report(it, "On-demand download of ${entry.fileId} failed")
                }
        } finally {
            downloading.update { it - key }
        }
    }
}

private fun SourceEntry.originRoot(metadata: List<PeerSourceMetadata>): String =
    metadata.initiatorHalfOf(this)?.storagePath ?: label
