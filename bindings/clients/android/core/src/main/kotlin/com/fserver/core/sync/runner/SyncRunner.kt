package com.fserver.core.sync.runner

import com.fserver.core.di.BackgroundScope
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.SourceEntry
import com.fserver.core.sync.index.LocalChangesIndexer
import com.fserver.core.sync.index.toFileRecord
import com.fserver.core.sync.remote.PeerIndexFetcher
import com.fserver.files.upload.FilesSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Walks every registered source by request.
 *
 * No timer of its own: `:core` ships as a standalone library, so it cannot assume WorkManager or
 * edit the host's manifest. A host that wants periodic passes schedules its own job and calls
 * `SourcesController.runSync`.
 */
internal class SyncRunner(
    private val storage: FServerStorage,
    private val localIndexer: LocalChangesIndexer,
    private val remoteFetcher: PeerIndexFetcher,
    private val uploadStrategySelector: UploadStrategySelector,
    private val backgroundScope: BackgroundScope,
) {
    private val mutex = Mutex()

    /** One pass over every registered source. */
    suspend fun runOnce() = mutex.withLock {
        for (source in storage.sources.all()) {
            try {
                process(source)
            } catch (e: Exception) {
                if (e is CancellationException) throw e

                Timber.e(e, "Source pass failed for ${source.id}")
            }
        }
    }

    /** One pass over every registered source, launched on the engine's background scope. */
    fun runOnceAsync() = backgroundScope.launch {
        try {
            if (mutex.isLocked) {
                Timber.w("One-off source pass skipped: another pass is still running")
                return@launch
            }

            runOnce()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "One-off source pass failed")
        }
    }

    private suspend fun process(source: SourceEntry) {
        val local = localIndexer.refresh(source)
        val remote = remoteFetcher.fetchIndex(source)

        val snapshot = FilesSnapshot(
            local = local.map { it.toFileRecord() },
            remote = remote,
        )

        val decisions = uploadStrategySelector.plan(source.syncMode, snapshot)

        // TODO: executor.
        //  1. Resolve FileAction.ComputeHash first, then re-plan: hash local bytes, ask the peer for
        //     remote ones. Cap the rounds - a strategy bug must not spin here forever.
        //  2. Execute the remaining actions. Transfers already stream the bytes, so hash in the same
        //     pass instead of reading the file twice.
        //  3. Download writes the index record together with the bytes, carrying the peer's fileId,
        //     revision and hash. Minting our own re-owns the file, and every later peer edit then
        //     reads as a false Conflict; writing no record at all makes the next scan mint a fresh
        //     fileId, and the pair ping-pongs upload/download every pass.
        //  4. Restore the peer's lastModified on the written file so the next scan sees no change.
        //     Not always possible through MediaStore/SAF, so step 3 has to hold on its own.
        //  5. Mark processed only what actually succeeded; failed actions stay for the next pass.
    }
}
