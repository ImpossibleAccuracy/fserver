package com.fserver.core.sync.runner

import com.fserver.core.di.BackgroundScope
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.SourceEntry
import com.fserver.core.sync.index.LocalChangesIndexer
import com.fserver.core.sync.remote.PeerIndexFetcher
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

        //TODO:
        // 1. compute snapshot
        // 2. run strategy to get actions
        // 3. execute actions
        // 4. mark processed or re-run (if some actions need to be retried)
    }
}
