package com.fserver.core.files

import com.fserver.common.task.ProgressTask
import com.fserver.common.task.map
import com.fserver.core.di.BackgroundScope
import com.fserver.core.files.scan.DirectoryScanProgress
import com.fserver.core.files.scan.ScannedFile
import com.fserver.core.files.scan.toCore
import com.fserver.core.files.scan.toFiles
import com.fserver.core.requirement.RequirementsChecker
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.index.RemoteIndexedFile
import com.fserver.files.FilesNode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class FilesController internal constructor(
    private val node: FilesNode,
    private val storage: FServerStorage,
    private val requirementsChecker: RequirementsChecker,
    private val coroutineScope: BackgroundScope,
) {
    /** Scan the given directory and load the content of the files. */
    suspend fun loadContent(directory: SourceLocation): ProgressTask<DirectoryScanProgress, List<ScannedFile>> {
        requirementsChecker.ensureSourceReachable(directory)

        return node.openSource(directory.toFiles())
            .scan()
            .map(
                progressMapper = { it.toCore() },
                resultMapper = { list ->
                    list.map { it.toCore() }
                },
            )
    }

    /** Observes the overall content of the local and remote indexed files. */
    val overallContent: StateFlow<List<SyncFileEntry>> = combine(
        storage.index.all,
        storage.remoteIndex.all,
    ) { local, remote ->
        val localBySource = local.groupBy { it.sourceId }
        val remoteBySource = remote.groupBy { it.sourceId }

        val sources = localBySource.keys + remoteBySource.keys

        sources.flatMap {
            mergeIndexedFiles(
                local = localBySource[it] ?: emptyList(),
                remote = remoteBySource[it] ?: emptyList(),
            )
        }
    }.stateIn(
        scope = coroutineScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /** Merges the local and remote indexed files within single source. */
    private fun mergeIndexedFiles(
        local: List<LocalIndexedFile>,
        remote: List<RemoteIndexedFile>
    ): List<SyncFileEntry> {
        val result = mutableListOf<SyncFileEntry>()

        val localById = local.associateBy { it.fileId }
        val remoteById = remote.associateBy { it.fileId }
        val fileIds = localById.keys + remoteById.keys

        for (file in fileIds) {
            val local = localById[file]?.takeUnless { it.isDeleted }
            val remote = remoteById[file]?.takeUnless { it.isDeleted }

            if (local != null && remote != null) {
                result += local.toSyncEntry(
                    remoteState = remote.state,
                )
            } else if (local != null) {
                result += local.toSyncEntry()
            } else if (remote != null) {
                result += remote.toSyncEntry()
            }
        }

        return result
    }
}

private fun LocalIndexedFile.toSyncEntry(
    remoteState: LocalIndexedFile.State? = null,
) = SyncFileEntry(
    fileId = fileId,
    sourceId = sourceId,
    path = path,
    locator = locator,
    size = size,
    localState = state,
    remoteState = remoteState,
    modifiedAt = modifiedAt,
    revision = revision,
)

private fun RemoteIndexedFile.toSyncEntry() = SyncFileEntry(
    fileId = fileId,
    sourceId = sourceId,
    path = path,
    locator = null,
    size = size,
    localState = null,
    remoteState = state,
    modifiedAt = modifiedAt,
    revision = revision,
)
