package com.fserver.core.sync.runner

import com.fserver.common.exception.SyncException
import com.fserver.core.di.BackgroundScope
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.SourceEntry
import com.fserver.core.sync.index.LocalChangesIndexer
import com.fserver.core.sync.index.toFileRecord
import com.fserver.core.sync.remote.PeerIndexFetcher
import com.fserver.files.upload.FileAction
import com.fserver.files.upload.FileId
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
    private val actionRunner: FileActionRunner,
    private val backgroundScope: BackgroundScope,
) {
    private val mutex = Mutex()

    /** One pass over every registered source. Waits for a pass already running. */
    suspend fun runOnce() = mutex.withLock { runPass() }

    /**
     * One pass over every registered source, launched on the engine's background scope.
     *
     * Skips rather than queues: the pass reads the whole world every time, so a second one right
     * behind the first would only redo its work.
     */
    fun runOnceAsync() = backgroundScope.launch {
        if (!mutex.tryLock()) {
            Timber.w("One-off source pass skipped: another pass is still running")
            return@launch
        }

        try {
            runPass()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "One-off source pass failed")
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun runPass() {
        for (source in storage.sources.all()) {
            try {
                process(source)
            } catch (e: Exception) {
                if (e is CancellationException) throw e

                Timber.e(e, "Source pass failed for ${source.id}")
            }
        }
    }

    /**
     * Plan and execute until nothing is left to hash.
     *
     * A [FileAction.ComputeHash] means the strategy planned that one file without knowing its
     * content, so only that file's other actions wait for the next round - the rest of the source
     * runs now. Failures are collected instead of thrown: one unreachable file must not cancel the
     * hash rounds the others are waiting on.
     */
    private suspend fun process(source: SourceEntry) {
        val errors = mutableListOf<Throwable>()
        val handled = mutableSetOf<FileId>()

        // Disk is scanned once: hashing writes to the index only, so later rounds re-read it.
        var local = localIndexer.refresh(source)
        var round = 0

        while (true) {
            val snapshot = FilesSnapshot(
                local = local.map { it.toFileRecord() },
                remote = remoteFetcher.fetchIndex(source),
            )

            val decisions = uploadStrategySelector.plan(source.syncMode, snapshot)
            if (decisions.isEmpty) break

            val unhashed = decisions.filterIsAction<FileAction.ComputeHash>()
                .mapTo(mutableSetOf(), FileAction.ComputeHash::id)

            for (action in decisions.actions) {
                if (action !is FileAction.ComputeHash) {
                    // Planned from unknown content - re-plan it once the hash lands.
                    if (action.id in unhashed) continue

                    // One action per file per pass: a later round re-plans from an index the
                    // transfer has not written back yet, and would hand out the same action twice.
                    if (!handled.add(action.id)) continue
                }

                actionRunner.execute(source, action)
                    .exceptionOrNull()
                    ?.let(errors::add)
            }

            if (unhashed.isEmpty()) break

            if (++round > MaxRounds) {
                errors += SyncException.MaxRetriesExceededException(
                    "Source ${source.id} still has ${unhashed.size} unhashed files after $MaxRounds rounds",
                )
                break
            }

            local = storage.index.processedFiles(source.id)
        }

        if (errors.isEmpty()) return

        val failure = SyncException.ActionFailedException(
            "Source ${source.id} pass failed with ${errors.size} errors",
        )
        errors.forEach(failure::addSuppressed)

        throw failure
    }

    companion object {
        private const val MaxRounds = 3
    }
}
