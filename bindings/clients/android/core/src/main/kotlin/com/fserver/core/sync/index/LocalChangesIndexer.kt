package com.fserver.core.sync.index

import com.fserver.common.model.ContentHash
import com.fserver.common.utils.IdGenerator
import com.fserver.common.utils.SourcePaths
import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.files.ensureSourceReachable
import com.fserver.core.files.scan.toFiles
import com.fserver.core.requirement.RequirementsChecker
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.progress.impl.SyncProgressReporter
import com.fserver.core.util.TimeProvider
import com.fserver.files.FilesNode
import com.fserver.files.fs.FoundFile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Instant

/** Brings a source's local index in line with its folder. Writes outside a scan go through [LocalIndexWriter]. */
internal class LocalChangesIndexer(
    private val store: FServerStorage,
    private val node: FilesNode,
    private val requirementsChecker: RequirementsChecker,
    private val timeProvider: TimeProvider,
    private val locks: SourceIndexLocks,
    private val versions: LocalVersions,
    private val hasher: LocalFileHasher,
    private val progress: SyncProgressReporter,
) {
    private val renameHashLocks = ConcurrentHashMap<String, Mutex>()

    /**
     * Re-scan [source] and bring the index in line with what is on disk, then hash the files that
     * may be renames (see [renameCandidates]). Reports its own progress.
     *
     * Serialized per source: a local pass and a peer's index request both land here, and two
     * scans writing the same rows interleave into double-bumped version vectors.
     */
    suspend fun refresh(source: SourceEntry): List<LocalIndexedFile> {
        progress.indexingStarted(source.id)

        try {
            val indexed = locks.withLock(source.id) { runRefresh(source) }
            val rehashed = hashRenameCandidates(source)

            progress.indexingFinished(source.id, failed = false)
            return if (rehashed) store.index.processedFiles(source.id) else indexed
        } catch (e: Throwable) {
            progress.indexingFinished(source.id, failed = true)
            throw e
        }
    }

    /**
     * Hashes outside the index lock, so a long read does not hold scans up; its own lock keeps a
     * pass and a peer's index request from reading the same files twice.
     *
     * @return whether anything was hashed
     */
    private suspend fun hashRenameCandidates(source: SourceEntry): Boolean = renameHashLockFor(source).withLock {
        val candidates = renameCandidates(store.index.processedFiles(source.id))
        if (candidates.isEmpty()) return@withLock false

        candidates.forEachIndexed { done, row ->
            progress.indexingHashing(source.id, done, candidates.size)

            runCatchingCancellable { hasher.hashFile(source, row) }
                .onFailure { Timber.w(it, "Could not hash rename candidate ${row.path} in source ${source.id}") }
        }
        progress.indexingHashing(source.id, candidates.size, candidates.size)

        true
    }

    private suspend fun runRefresh(source: SourceEntry): List<LocalIndexedFile> {
        requirementsChecker.ensureSourceReachable(source.location)

        val currentTime = timeProvider.now()

        val savedState = store.index.processedFiles(source.id)
        val savedByPath = savedState
            .associateBy { it.path }
            .toMutableMap()

        // TODO: filter out temp files
        val scan = node.openSource(source.location.toFiles()).scan()
        scan.progress.collect { progress.indexingScanned(source.id, it.scannedFiles, it.scannedSizeBytes) }
        val actualState = scan.result().getOrThrow()

        val new = mutableListOf<FoundFile>()
        val changed = mutableMapOf<LocalIndexedFile, FoundFile>()
        val touched = mutableMapOf<LocalIndexedFile, FoundFile>()

        for (file in actualState) {
            val saved = savedByPath.remove(file.path)
            when {
                saved == null -> new += file

                // File was deleted or evicted, and now is back
                saved.state !is LocalIndexedFile.State.Present -> changed[saved] = file

                file.size != saved.size -> changed[saved] = file

                // Same size, new mtime: only a hash can tell an edit from a touch, so the version
                // waits for it - unless there is no earlier hash to compare with.
                file.lastModified != saved.modifiedAt ->
                    if (saved.hash == null) changed[saved] = file else touched[saved] = file
            }
        }

        val toDelete = savedByPath.values.filter {
            it.state is LocalIndexedFile.State.Present
        }

        // Every certain change is a new version: a new file, new bytes, or a deletion.
        val versions = this.versions.issuer(new.size + changed.size + toDelete.size)

        val toSave =
            ArrayList<LocalIndexedFile>(new.size + changed.size + touched.size + toDelete.size).apply {
                for (file in new) {
                    this += file.toIndexed(
                        id = IdGenerator.nextId,
                        sourceId = source.id,
                        fileId = SourcePaths.fileId(file.path),
                        state = LocalIndexedFile.State.Present(
                            pinned = false,
                        ),
                        version = versions.after(null),
                        hash = null,
                        currentTime = currentTime,
                    )
                }

                for ((saved, file) in changed) {
                    this += file.toIndexed(
                        id = saved.id,
                        sourceId = source.id,
                        fileId = saved.fileId,
                        state =
                            // Restore file if it was deleted and now is back
                            saved.state as? LocalIndexedFile.State.Present
                                ?: LocalIndexedFile.State.Present(
                                    pinned = false,
                                ),
                        version = versions.after(saved.version),
                        hash = null,
                        currentTime = currentTime,
                    )
                }

                for ((saved, file) in touched) {
                    this += file.toIndexed(
                        id = saved.id,
                        sourceId = source.id,
                        fileId = saved.fileId,
                        state = saved.state,
                        version = saved.version,
                        hash = saved.hash,
                        hashStale = true,
                        currentTime = currentTime,
                    )
                }

                // A deletion is a version like any other, so it can be ordered against a remote edit.
                // hashStale is kept: a rename is matched by the tombstone's hash.
                for (saved in toDelete) {
                    this += saved.copy(
                        state = LocalIndexedFile.State.Deleted(deletedAt = currentTime),
                        version = versions.after(saved.version),
                        processedAt = currentTime,
                    )
                }
            }

        store.index.markProcessed(toSave)

        // Return full state after all writes
        return store.index.processedFiles(source.id)
    }

    private fun renameHashLockFor(source: SourceEntry): Mutex =
        renameHashLocks.computeIfAbsent(source.id) { Mutex() }
}

/**
 * Unhashed present files that may be a rename: same size and mtime as a tombstone with a trusted
 * hash. A file never sent needs no rename, and a sent one was hashed on the way.
 */
internal fun renameCandidates(rows: List<LocalIndexedFile>): List<LocalIndexedFile> {
    val tombstones = rows
        .filter { it.isDeleted && it.hash != null && !it.hashStale }
        .mapTo(HashSet()) { it.size to it.modifiedAt }
    if (tombstones.isEmpty()) return emptyList()

    return rows.filter {
        it.state is LocalIndexedFile.State.Present && it.hash == null && (it.size to it.modifiedAt) in tombstones
    }
}

private fun FoundFile.toIndexed(
    id: String,
    sourceId: String,
    fileId: String,
    state: LocalIndexedFile.State,
    version: LocalIndexedFile.Version?,
    hash: ContentHash?,
    hashStale: Boolean = false,
    currentTime: Instant,
) = LocalIndexedFile(
    id = id,
    sourceId = sourceId,
    fileId = fileId,
    path = path,
    locator = locator,
    state = state,
    size = size,
    modifiedAt = lastModified,
    hash = hash,
    hashStale = hashStale,
    version = version,
    processedAt = currentTime,
)
