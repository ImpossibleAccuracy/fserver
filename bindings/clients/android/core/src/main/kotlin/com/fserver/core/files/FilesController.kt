package com.fserver.core.files

import com.fserver.common.exception.SyncException
import com.fserver.common.task.ProgressTask
import com.fserver.common.task.map
import com.fserver.common.utils.SourcePaths
import com.fserver.common.utils.runBackgroundJob
import com.fserver.core.di.BackgroundScope
import com.fserver.core.files.access.LocalFileEditor
import com.fserver.core.files.access.SourceFile
import com.fserver.core.files.scan.DirectoryScanProgress
import com.fserver.core.files.scan.ScannedContent
import com.fserver.core.files.scan.toCore
import com.fserver.core.files.scan.toFiles
import com.fserver.core.requirement.RequirementsChecker
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.index.RemoteIndexedFile
import com.fserver.core.sync.fileops.FileEvictor
import com.fserver.core.sync.model.evictsByHand
import com.fserver.core.sync.model.evictsLocally
import com.fserver.core.sync.model.fetches
import com.fserver.core.sync.runner.SyncRunner
import com.fserver.core.sync.transfer.FileDownloader
import com.fserver.core.util.TimeProvider
import com.fserver.files.FilesNode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class FilesController internal constructor(
    private val node: FilesNode,
    private val storage: FServerStorage,
    private val requirementsChecker: RequirementsChecker,
    private val fileDownloader: FileDownloader,
    private val editor: LocalFileEditor,
    private val evictor: FileEvictor,
    private val syncRunner: SyncRunner,
    private val timeProvider: TimeProvider,
    private val coroutineScope: BackgroundScope,
) {
    /** Scan the given directory and load the content of the files. */
    suspend fun loadContent(directory: SourceLocation): ProgressTask<DirectoryScanProgress, ScannedContent> {
        requirementsChecker.ensureSourceReachable(directory)

        return node.openSource(directory.toFiles())
            .scanTree()
            .map(
                progressMapper = { it.toCore() },
                resultMapper = { it.toCore() },
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

    /** [sourceId]'s part of [overallContent], read from the index now: a snapshot, never the empty initial value. */
    suspend fun content(sourceId: String): List<SyncFileEntry> = mergeIndexedFiles(
        local = storage.index.processedFiles(sourceId),
        remote = storage.remoteIndex.files(sourceId),
    )

    /** The entry at source-relative [path] of [sourceId], or null when neither side holds a file there. */
    suspend fun entry(sourceId: String, path: String): SyncFileEntry? {
        val key = IndexedFileKey(fileId = SourcePaths.fileId(path), sourceId = sourceId)
        return mergeIndexedFiles(
            local = listOfNotNull(storage.index.findFile(key)),
            remote = listOfNotNull(storage.remoteIndex.findFile(key)),
        ).singleOrNull()
    }

    /**
     * Fetches a file this device does not hold from its source's peer, into the source itself: a
     * pass then sees both sides equal. Where the source evicts, the copy is marked fetched, and is
     * evicted again once its TTL runs out (see [com.fserver.core.files.gc.GarbageCollector]).
     *
     * @return the entry as indexed here now, with a [SyncFileEntry.locator] to open it by.
     */
    suspend fun download(entry: SyncFileEntry): Result<SyncFileEntry> = runBackgroundJob {
        val key = IndexedFileKey(fileId = entry.fileId, sourceId = entry.sourceId)

        storage.index.findFile(key)?.presentEntry(entry.remoteState)
            ?.let { return@runBackgroundJob it }

        val source = storage.sources.findById(entry.sourceId)
            ?: throw IllegalArgumentException("Source ${entry.sourceId} is not registered")

        if (!source.fetches(storage.index.findFile(key)?.state)) {
            throw SyncException.ModeForbiddenException(
                "Source ${source.id} fetches nothing on demand under ${source.syncMode.type}"
            )
        }

        requirementsChecker.ensureSourceReachable(source.location)

        fileDownloader.download(source = source, key = key, sizeBytes = entry.size.bytes)

        if (source.evictsLocally) markFetched(key)

        storage.index.findFile(key)?.presentEntry(entry.remoteState)
            ?: throw IllegalStateException("File ${entry.fileId} was not indexed after download")
    }

    /** File [fileId] of [sourceId] as indexed here, or null when this device has no row for it. */
    suspend fun file(sourceId: String, fileId: String): SourceFile? = editor.find(sourceId, fileId)

    /**
     * Creates an empty file at [path] - source-relative, as [SyncFileEntry.path] - and indexes it.
     * Fill it with [SourceFile.write].
     */
    suspend fun create(sourceId: String, path: String): SourceFile = editor.create(sourceId, path)

    /**
     * Deletes [fileId] of [sourceId], and the deletion reaches the peer. An evicted file is deleted
     * too; one this device has no row for is left alone.
     */
    suspend fun delete(sourceId: String, fileId: String) =
        editor.delete(IndexedFileKey(fileId = fileId, sourceId = sourceId))

    /**
     * Evicts the local bytes of [fileIds] in [sourceId], where [evictsByHand] allows it. A pass runs
     * first and eviction happens under its lease, against the peer's index as it is now: the peer
     * cannot evict the same file meanwhile. One with an [EvictRefusal] by then is skipped; nothing is
     * evicted when the pass fails or cannot run.
     *
     * @return the ids actually evicted.
     */
    suspend fun evict(sourceId: String, fileIds: Set<String>): Result<Set<String>> = runBackgroundJob {
        val source = storage.sources.findById(sourceId)
            ?: throw IllegalArgumentException("Source $sourceId is not registered")

        if (!source.evictsByHand) {
            throw SyncException.ModeForbiddenException(
                "Source ${source.id} is not evicted by hand under ${source.syncMode.type} as ${source.role}"
            )
        }

        syncRunner.runSourceThen(source) { agreed ->
            fileIds.filterTo(mutableSetOf()) { fileId ->
                val row = storage.index.findFile(IndexedFileKey(fileId = fileId, sourceId = sourceId))
                row != null && evictor.evict(agreed, fileId, expected = row.hash)
            }
        }
    }

    private suspend fun markFetched(key: IndexedFileKey) {
        val present = storage.index.findFile(key)?.state as? LocalIndexedFile.State.Present
            ?: return
        storage.index.updateFileState(key, present.copy(fetchedAt = timeProvider.now()))
    }

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
            val local = localById[file]
            if (local?.isDeleted == true) continue

            val remote = remoteById[file]?.takeUnless { it.isDeleted }

            if (local != null && remote != null) {
                result += local.toSyncEntry(
                    remoteState = remote.state,
                    lostOnPeer = local.lostOn(remote),
                    evictRefusal = local.evictRefusal(remote),
                )
            } else if (local != null) {
                result += local.toSyncEntry(
                    lostOnPeer = local.lostOn(remote = null),
                    evictRefusal = local.evictRefusal(remote = null),
                )
            } else if (remote != null) {
                result += remote.toSyncEntry()
            }
        }

        return result
    }
}

private fun LocalIndexedFile.presentEntry(remoteState: LocalIndexedFile.State?): SyncFileEntry? =
    takeIf { it.state is LocalIndexedFile.State.Present }?.toSyncEntry(remoteState)

/** Evicted here while [remote] (null when gone) no longer holds these bytes. An unhashed remote is trusted. */
private fun LocalIndexedFile.lostOn(remote: RemoteIndexedFile?): Boolean =
    state is LocalIndexedFile.State.Evicted && (remote == null || (remote.hash != null && remote.hash != hash))

private fun LocalIndexedFile.toSyncEntry(
    remoteState: LocalIndexedFile.State? = null,
    lostOnPeer: Boolean = false,
    evictRefusal: EvictRefusal? = EvictRefusal.NotHere,
) = SyncFileEntry(
    fileId = fileId,
    sourceId = sourceId,
    path = path,
    // An evicted file keeps its old locator in the index, but there is nothing behind it to open.
    locator = locator.takeIf { state is LocalIndexedFile.State.Present },
    size = size,
    localState = state,
    remoteState = remoteState,
    modifiedAt = modifiedAt,
    lostOnPeer = lostOnPeer,
    evictRefusal = evictRefusal,
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
    evictRefusal = EvictRefusal.NotHere,
)
