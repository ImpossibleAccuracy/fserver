package com.fserver.core.files.gc

import com.fserver.common.exception.FileSystemException
import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.di.BackgroundScope
import com.fserver.core.files.scan.toFiles
import com.fserver.core.oneshot.impl.OneShotOutbox
import com.fserver.core.sync.server.handler.upload.oneshot.OneShotStaging
import com.fserver.core.oneshot.model.OneShotTransfer
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.fileops.FileEvictor
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.evictsLocally
import com.fserver.core.util.TimeProvider
import com.fserver.files.FilesNode
import com.fserver.files.fs.FsFile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import timber.log.Timber
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Component that drops uploads nobody came back for, rows whose bytes are gone, bytes with no row,
 * and copies fetched on demand once they outlived [FetchedTtl].
 */
internal class GarbageCollector(
    private val storage: FServerStorage,
    private val node: FilesNode,
    private val timeProvider: TimeProvider,
    private val backgroundScope: BackgroundScope,
    private val fileEvictor: FileEvictor,
) {
    private val gcLock = Mutex()

    private val staging get() = node.openStaging()

    /** [collectGarbage] off the caller's coroutine; skipped while one is already running. */
    fun collectGarbageAsync() {
        backgroundScope.launch {
            if (!gcLock.tryLock()) return@launch

            try {
                runCatchingCancellable { collectGarbage() }
                    .onFailure { Timber.w(it, "Upload staging GC failed") }
            } finally {
                gcLock.unlock()
            }
        }
    }

    /** Collect every kind of garbage that code can create. */
    suspend fun collectGarbage() {
        val now = timeProvider.now()
        collectStaleUploads(now)
        collectOrphanStagedFiles(now)
        collectOneShotStaging()
        collectOneShotOutbox(now)
        collectExpiredFetches(now)
    }

    /** Drops uploads whose bytes are gone or which have not been touched for a while. */
    private suspend fun collectStaleUploads(now: Instant) {
        val parked = storage.uploads.all()

        for (upload in parked) {
            val key = IndexedFileKey(fileId = upload.fileId, sourceId = upload.sourceId)
            val staged = openOrNull(upload.locator)

            // A row with no bytes is cache the system cleared: nothing left to resume.
            if (staged == null || now - upload.touchedAt > StagedTtl) {
                Timber.i("Dropping staged upload $key, touched at ${upload.touchedAt}")
                staged?.delete()
                storage.uploads.delete(key)
            }
        }
    }

    /**
     * Drops staged files with no row.
     * TODO: age is the whole policy for now. Add a cap on total staged bytes, per peer too.
     */
    private suspend fun collectOrphanStagedFiles(now: Instant) {
        val known = storage.uploads.all().mapTo(HashSet()) { it.locator }
        val found = staging.scan().result().getOrThrow()

        for (orphan in found) {
            // Young ones may be an upload between creating its file and writing its row.
            if (orphan.locator in known || now - orphan.lastModified < OrphanGrace) continue

            // Owned by the transfer's record, not a staged-upload row: collectOneShotStaging's call.
            if (OneShotStaging.transferIdOf(orphan.path) != null) continue

            Timber.i("Dropping orphan staged file ${orphan.path}")
            openOrNull(orphan.locator)?.delete()
        }
    }

    /** Drops one-shot staging of any transfer that is no longer receiving. */
    private suspend fun collectOneShotStaging() {
        val receiving = storage.oneShotTransfers.unfinished()
            .filter { it.direction is OneShotTransfer.Direction.Incoming && it.status == OneShotTransfer.Status.Active }
            .mapTo(HashSet()) { it.id }

        for (file in staging.scan().result().getOrThrow()) {
            val transferId = OneShotStaging.transferIdOf(file.path) ?: continue
            if (transferId in receiving) continue

            Timber.i("Dropping staging of finished transfer $transferId")
            openOrNull(file.locator)?.delete()
        }
    }

    /** Drops outbox copies of transfers that ended, or were never recorded. */
    private suspend fun collectOneShotOutbox(now: Instant) {
        val outbox = node.openSource(OneShotOutbox.Location.toFiles())
        val unfinished = storage.oneShotTransfers.unfinished().mapTo(HashSet()) { it.id }

        for (file in outbox.scan().result().getOrThrow()) {
            val transferId = OneShotOutbox.transferIdOf(file.path)

            val keep = when {
                transferId == null -> false
                transferId in unfinished -> true
                storage.oneShotTransfers.find(transferId) != null -> false
                // No row yet: a transfer between copying its files and recording itself.
                else -> now - file.lastModified < OrphanGrace
            }
            if (keep) continue

            Timber.i("Dropping outbox copy ${file.path}")
            runCatchingCancellable { outbox.openFile(file.locator)?.delete() }
        }
    }

    /** Evicts again what was fetched on demand, once the user had [FetchedTtl] to work with it. */
    private suspend fun collectExpiredFetches(now: Instant) {
        val sources = storage.sources.all().filter { it.evictsLocally && it.status == SourceEntry.Status.Active }

        for (source in sources) {
            for (file in storage.index.processedFiles(source.id)) {
                val fetchedAt = (file.state as? LocalIndexedFile.State.Present)?.fetchedAt ?: continue
                if (now - fetchedAt < FetchedTtl) continue

                runCatchingCancellable { fileEvictor.evict(source, file.fileId, expected = file.hash) }
                    .onFailure { Timber.w(it, "Could not evict fetched ${file.path} in source ${source.id}") }
            }
        }
    }

    private suspend fun openOrNull(locator: String): FsFile? =
        try {
            staging.openFile(locator)
        } catch (_: FileSystemException.InvalidPath) {
            null
        }

    companion object {
        /** Since the last checkpoint. The peer resumes on its next pass, which is sooner than this. */
        val StagedTtl = 1.days

        /** Young files may be an upload between creating its file and writing its row. */
        val OrphanGrace = 1.hours

        /** How long a file fetched on demand stays before it is evicted again. TODO: make configurable. */
        val FetchedTtl = 1.days
    }
}
