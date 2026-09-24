package com.fserver.core.files.gc

import com.fserver.common.exception.FileSystemException
import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.di.BackgroundScope
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.index.IndexedFileKey
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
 * Component that drops uploads nobody came back for, rows whose bytes are gone, and bytes with no row.
 */
internal class GarbageCollector(
    private val storage: FServerStorage,
    private val node: FilesNode,
    private val timeProvider: TimeProvider,
    private val backgroundScope: BackgroundScope,
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

            Timber.i("Dropping orphan staged file ${orphan.path}")
            openOrNull(orphan.locator)?.delete()
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
    }
}
