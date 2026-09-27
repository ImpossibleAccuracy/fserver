package com.fserver.core.sync.runner

import com.fserver.common.exception.SyncException
import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.di.BackgroundScope
import com.fserver.core.files.gc.GarbageCollector
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.conflict.settledDecisions
import com.fserver.core.sync.device.DeviceConstraintChecker
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.index.LocalChangesIndexer
import com.fserver.core.sync.index.toFileRecord
import com.fserver.core.sync.lease.SyncLeaseNegotiator
import com.fserver.core.sync.limits.limit
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.drivesSync
import com.fserver.core.sync.progress.SourcePass
import com.fserver.core.sync.progress.SyncProgressReporter
import com.fserver.core.sync.remote.IndexPublisher
import com.fserver.core.sync.remote.PeerIndexFetcher
import com.fserver.core.sync.setup.SourceSetupExchange
import com.fserver.core.util.TimeProvider
import com.fserver.files.upload.FileAction
import com.fserver.files.upload.FileId
import com.fserver.files.upload.FilesSnapshot
import com.fserver.files.upload.UploadDecisions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Walks every registered source by request.
 *
 * No timer of its own: host that wants periodic passes schedules its own job and calls `SourcesController.runSync`.
 */
internal class SyncRunner(
    private val storage: FServerStorage,
    private val localIndexer: LocalChangesIndexer,
    private val remoteFetcher: PeerIndexFetcher,
    private val indexPublisher: IndexPublisher,
    private val uploadStrategySelector: UploadStrategySelector,
    private val actionRunner: FileActionRunner,
    private val constraintChecker: DeviceConstraintChecker,
    private val leaseNegotiator: SyncLeaseNegotiator,
    private val progress: SyncProgressReporter,
    private val garbageCollector: GarbageCollector,
    private val sourceSetup: SourceSetupExchange,
    private val backgroundScope: BackgroundScope,
    private val timeProvider: TimeProvider,
) {
    private val mutex = Mutex()

    /** One pass over every registered source. Waits for a pass already running. */
    suspend fun runOnce() = mutex.withLock { runPass(storage.sources.all()) }

    /**
     * One pass over [sourceId] alone. Waits for a pass already running. [force] skips the device
     * constraints - the user asked for this pass explicitly.
     */
    suspend fun runSource(sourceId: String, force: Boolean) = mutex.withLock {
        runPass(listOfNotNull(storage.sources.findById(sourceId)), force)
    }

    /** [runSource] on the background scope. Waits for a pass already running rather than skipping. */
    fun runSourceAsync(sourceId: String): Job = backgroundScope.launch {
        runCatchingCancellable { runSource(sourceId, force = false) }
            .onFailure { Timber.w(it, "Source pass ($sourceId) failed") }
    }

    /**
     * One pass over every registered source, launched on the engine's background scope.
     * Skips if a pass is already running.
     */
    fun runOnceAsync(): Job = launchPass("one-off") { storage.sources.all() }

    /**
     * One pass over the sources paired with [deviceId] only, launched on the background scope.
     * Skips if a pass is already running - that pass covers this device too.
     */
    fun runForDeviceAsync(deviceId: String): Job = launchPass("device $deviceId") {
        storage.sources.all().filter { it.deviceId == deviceId }
    }

    private fun launchPass(label: String, select: suspend () -> List<SourceEntry>): Job =
        backgroundScope.launch {
            if (!mutex.tryLock()) {
                Timber.w("Source pass ($label) skipped: another pass is still running")
                return@launch
            }

            try {
                runPass(select())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Source pass ($label) failed")
            } finally {
                mutex.unlock()
            }
        }

    private suspend fun runPass(sources: List<SourceEntry>, force: Boolean = false) {
        for (source in sources) {
            try {
                process(source, force)

                Timber.i("Source ${source.id} pass completed successfully")
            } catch (e: Exception) {
                if (e is CancellationException) throw e

                Timber.e(e, "Source pass failed for ${source.id}")
            }
        }

        garbageCollector.collectGarbageAsync()
        askPendingAsync(sources)
    }

    /**
     * Re-asks the peer of every source still pending here. It may have accepted while we were
     * offline, and a peer that already answered answers again instead of asking its user twice.
     */
    private fun askPendingAsync(sources: List<SourceEntry>) {
        val pending = sources.filter {
            it.role == SourceEntry.Role.Initiator && it.status == SourceEntry.Status.Pending
        }
        if (pending.isEmpty()) return

        backgroundScope.launch {
            for (source in pending) {
                val current = storage.sources.findById(source.id)
                    ?.takeIf { it.status == SourceEntry.Status.Pending }
                    ?: continue

                runCatchingCancellable { sourceSetup.requestRemote(current) }
                    .exceptionOrNull()
                    ?.let { Timber.w(it, "Could not re-ask ${current.deviceId} to host source ${current.id}") }
            }
        }
    }

    /** One source, under a lease the peer agreed to. */
    private suspend fun process(source: SourceEntry, force: Boolean) {
        // Allow sync only active sources
        if (source.status != SourceEntry.Status.Active) {
            localIndexer.refresh(source) // refresh local index anyway
            Timber.i("Source ${source.id} skipped: ${source.status}")
            return
        }

        // The initiator drives a one-way source; this end only answers it.
        if (!source.drivesSync) {
            Timber.i("Source ${source.id} skipped: ${source.syncMode.type} runs from the initiator")
            return
        }

        val constraintsMet = force || constraintChecker(
            constraints = source.preferences.deviceConstraints,
        )

        if (!constraintsMet) {
            Timber.w("Source ${source.id} skipped: device constraints not met")
            return
        }

        // Set once the pass itself is on the record. Before that a failure is the lease - dialling
        // the peer, or asking it - and nothing else has reported it.
        var passReported = false

        try {
            leaseNegotiator.runWithLease(source) { agreed ->
                progress.localPassStarted(source.id)
                passReported = true

                try {
                    syncSource(agreed)
                } catch (e: Exception) {
                    progress.localPassFinished(source.id, e)
                    throw e
                }

                progress.localPassFinished(source.id, null)
                storage.sources.markSynced(source.id, timeProvider.now())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!passReported) progress.localPassAborted(source.id, e)
            throw e
        }

        // Off the pass on purpose: the loop moves to the next source without waiting on a message nobody answers
        backgroundScope.launch { indexPublisher.publish(source) }
    }

    /**
     * Plan and execute until nothing is left to hash.
     *
     * Run several rounds because the peer's index may have files with unknown content,
     * and the first round only hashes files that are already known locally.
     * Later rounds re-read the index and plan from it, until all files are hashed or a maximum number of rounds is reached.
     */
    private suspend fun syncSource(source: SourceEntry) {
        val errors = mutableListOf<Throwable>()
        val handled = mutableSetOf<FileId>()
        val skipped = mutableSetOf<FileId>()

        // Disk is scanned once: hashing writes to the index only, so later rounds re-read it.
        progress.localPassStage(source.id, SourcePass.Local.Stage.Scanning)
        var local = localIndexer.refresh(source)
        var round = 0

        while (true) {
            progress.localPassStage(source.id, SourcePass.Local.Stage.Planning)

            val snapshot = FilesSnapshot(
                local = local.map { it.toFileRecord() },
                remote = remoteFetcher.fetchIndex(source),
            )

            Timber.d("Source ${source.id} snapshot round $round: ${snapshot.local.size} local files, ${snapshot.remote.size} remote files")

            val decisions = uploadStrategySelector.plan(source, snapshot)
            dropSettledDecisions(source, decisions)
            if (decisions.isEmpty) break

            val unhashed = decisions.filterIsAction<FileAction.ComputeHash>()
                .mapTo(mutableSetOf(), FileAction.ComputeHash::id)

            val limited = source.preferences.fileLimits.limit(snapshot, decisions.actions)
            limited.runnable.forEach { skipped -= it.id }
            limited.overLimit.mapTo(skipped, FileAction::id)

            val runnable = limited.runnable.filter { action ->
                if (action is FileAction.ComputeHash) return@filter true

                // Planned from unknown content - re-plan it once the hash lands.
                if (action.id in unhashed) return@filter false

                // One action per file per pass: a later round re-plans from an index the
                // transfer has not written back yet, and would hand out the same action twice.
                handled.add(action.id)
            }

            progress.localPassPlanned(source.id, runnable)
            progress.localPassSkipped(source.id, skipped.size)

            for (action in runnable) {
                val failure = actionRunner.execute(source, action).exceptionOrNull()

                // The peer's limits turned it down: skipped like our own, not failed.
                if (failure is SyncException.OverLimitException) {
                    skipped += action.id
                    progress.localPassSkipped(source.id, skipped.size)
                    progress.localPassAdvanced(source.id, action, null)
                    continue
                }

                progress.localPassAdvanced(source.id, action, failure)
                failure?.let(errors::add)
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

    private suspend fun dropSettledDecisions(source: SourceEntry, plan: UploadDecisions) {
        val stored = storage.conflictDecisions.forSource(source.id)
        if (stored.isEmpty()) return

        for (decision in settledDecisions(source, plan, stored)) {
            // TODO: history entry - "your choice on <file> was overtaken" (resolved on the peer, or edited since).
            Timber.i("Dropping decision on ${decision.fileId} in source ${source.id}: no longer conflicts")
            storage.conflictDecisions.remove(IndexedFileKey(fileId = decision.fileId, sourceId = source.id))
        }
    }

    companion object {
        private const val MaxRounds = 3
    }
}
