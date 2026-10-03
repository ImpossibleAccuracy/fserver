package com.fserver.core.sync.runner.pass

import com.fserver.common.exception.NetworkException
import com.fserver.common.exception.SyncException
import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.journal.JournalEvent
import com.fserver.core.journal.PassTally
import com.fserver.core.journal.impl.JournalWriter
import com.fserver.core.network.DeviceUnreachableException
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.clock.ClockSkews
import com.fserver.core.sync.clock.PeerClockProbe
import com.fserver.core.sync.conflict.settledDecisions
import com.fserver.core.sync.device.DeviceConstraintChecker
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.index.LocalChangesIndexer
import com.fserver.core.sync.index.toFileRecord
import com.fserver.core.sync.lease.HeldLease
import com.fserver.core.sync.lease.SyncLeaseNegotiator
import com.fserver.core.sync.limits.limit
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.drivesSync
import com.fserver.core.sync.progress.SourcePass
import com.fserver.core.sync.progress.impl.SyncProgressReporter
import com.fserver.core.sync.remote.PeerIndexFetcher
import com.fserver.core.sync.remote.PeerSyncRequester
import com.fserver.core.sync.runner.UploadStrategySelector
import com.fserver.core.sync.runner.action.FileActionRunner
import com.fserver.core.util.TimeProvider
import com.fserver.files.upload.FileAction
import com.fserver.files.upload.FileId
import com.fserver.files.upload.FilesSnapshot
import com.fserver.files.upload.UploadDecisions
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/** One pass over one source: whether it may run, the lease for it, and the plan-execute rounds. */
internal class SourcePassExecutor(
    private val storage: FServerStorage,
    private val localIndexer: LocalChangesIndexer,
    private val remoteFetcher: PeerIndexFetcher,
    private val uploadStrategySelector: UploadStrategySelector,
    private val actionRunner: FileActionRunner,
    private val constraintChecker: DeviceConstraintChecker,
    private val leaseNegotiator: SyncLeaseNegotiator,
    private val progress: SyncProgressReporter,
    private val completion: PassCompletion,
    private val syncRequester: PeerSyncRequester,
    private val clockProbe: PeerClockProbe,
    private val clockSkews: ClockSkews,
    private val timeProvider: TimeProvider,
    private val journal: JournalWriter,
) {
    /**
     * One source, under a lease the peer agreed to. [force] skips the device constraints.
     *
     * [whileHeld] runs after a clean pass, still under the lease and with the peer's index just
     * re-fetched: the peer can change nothing meanwhile.
     */
    suspend fun process(
        source: SourceEntry,
        force: Boolean,
        whileHeld: (suspend (SourceEntry) -> Unit)? = null,
    ) {
        // Allow sync only active sources
        if (source.status != SourceEntry.Status.Active) {
            localIndexer.refresh(source) // refresh local index anyway
            Timber.i("Source ${source.id} skipped: ${source.status}")
            return
        }

        val constraintsMet = force || constraintChecker(
            constraints = source.preferences.deviceConstraints,
        )

        if (!constraintsMet) {
            Timber.w("Source ${source.id} skipped: device constraints not met")
            return
        }

        // The initiator drives a one-way source: ask it to run the pass by itself instead.
        if (!source.drivesSync) {
            syncRequester.requestAsync(source)
            Timber.i("Requested source ${source.id} pass from peer")
            return
        }

        // Set once the pass itself is on the record. Before that a failure is the lease - dialling
        // the peer, or asking it - and nothing else has reported it.
        var passReported = false

        try {
            leaseNegotiator.runWithLease(source) { lease ->
                val agreed = lease.source
                progress.localPassStarted(source.id)
                passReported = true

                val outcome = try {
                    syncSource(lease)
                } catch (e: Exception) {
                    progress.localPassFinished(source.id, e)
                    if (e !is CancellationException) journal.passFailed(source, e)
                    throw e
                }

                progress.localPassFinished(source.id, null)
                journal.passSucceeded(source, outcome.tally, outcome.conflicts)
                completion.localPassSucceeded(source)

                if (whileHeld != null) {
                    remoteFetcher.fetchIndex(agreed)
                    whileHeld(agreed)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!passReported) {
                progress.localPassAborted(source.id, e)
                journal.passFailed(source, e)
            }
            throw e
        }

        completion.localPassEnded(source)
    }

    /**
     * Plan and execute until nothing is left to hash.
     *
     * Run several rounds because the peer's index may have files with unknown content,
     * and the first round only hashes files that are already known locally.
     * Later rounds re-read the index and plan from it, until all files are hashed or a maximum number of rounds is reached.
     */
    private suspend fun syncSource(lease: HeldLease): PassOutcome {
        val source = lease.source
        val errors = mutableListOf<Throwable>()
        val handled = mutableSetOf<FileId>()
        val skipped = mutableSetOf<FileId>()
        val conflicts = mutableSetOf<String>()
        var tally = PassTally()

        // Before any conflict is resolved: a skewed peer holds the source to Ask.
        reconnecting(lease, errors) { runCatchingCancellable { clockProbe.measure(source) } }.getOrThrow()

        // Disk is scanned once: hashing writes to the index only, so later rounds re-read it.
        progress.localPassStage(source.id, SourcePass.Local.Stage.Scanning)
        var local = localIndexer.refresh(source)
        var round = 0

        while (true) {
            progress.localPassStage(source.id, SourcePass.Local.Stage.Planning)

            val snapshot = FilesSnapshot(
                local = local.map { it.toFileRecord() },
                remote = reconnecting(
                    lease,
                    errors
                ) { runCatchingCancellable { remoteFetcher.fetchIndex(source) } }.getOrThrow(),
                now = timeProvider.now(),
            )

            Timber.d("Source ${source.id} snapshot round $round: ${snapshot.local.size} local files, ${snapshot.remote.size} remote files")

            val decisions = uploadStrategySelector.plan(source, snapshot)
            dropSettledDecisions(source, decisions)
            decisions.filterIsAction<FileAction.Conflict>().mapTo(conflicts) { it.id.value }
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
                val failure = reconnecting(lease, errors) {
                    actionRunner.execute(
                        source,
                        action
                    )
                }.exceptionOrNull()

                // The peer's limits turned it down: skipped like our own, not failed.
                if (failure is SyncException.OverLimitException) {
                    skipped += action.id
                    progress.localPassSkipped(source.id, skipped.size)
                    progress.localPassAdvanced(source.id, action, null)
                    continue
                }

                progress.localPassAdvanced(source.id, action, failure)
                if (failure == null) tally = tally.counting(action) else errors += failure
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

        if (errors.isEmpty()) return PassOutcome(tally.copy(skipped = skipped.size), conflicts)

        val failure = SyncException.ActionFailedException(
            "Source ${source.id} pass failed with ${errors.size} errors",
        )
        errors.forEach(failure::addSuppressed)

        throw failure
    }

    /**
     * Runs [block], and again over a renewed lease if the link to the peer dropped under it.
     * Ends the pass, with [errors] so far, when the link cannot be brought back.
     */
    private suspend fun <T> reconnecting(
        lease: HeldLease,
        errors: List<Throwable>,
        block: suspend () -> Result<T>,
    ): Result<T> {
        val source = lease.source
        var result = block()

        repeat(MaxReconnects + 1) { attempt ->
            val lost = result.exceptionOrNull()?.takeIf { it.isLinkLoss() } ?: return result

            val renewed = attempt < MaxReconnects && runCatchingCancellable {
                Timber.w(lost, "Source ${source.id}: link to ${source.deviceId} lost, reconnecting")
                lease.renew()
            }.onFailure {
                Timber.w(
                    it,
                    "Source ${source.id}: could not reconnect to ${source.deviceId}"
                )
            }.isSuccess

            if (!renewed) {
                val aborted = SyncException.ActionFailedException(
                    "Source ${source.id} pass aborted: lost ${source.deviceId}",
                    lost
                )
                errors.forEach(aborted::addSuppressed)
                throw aborted
            }

            result = block()
        }

        return result
    }

    private suspend fun dropSettledDecisions(source: SourceEntry, plan: UploadDecisions) {
        val stored = storage.conflictDecisions.forSource(source.id)
        if (stored.isEmpty()) return

        for (decision in settledDecisions(clockSkews.resolution(source), plan, stored)) {
            Timber.i("Dropping decision on ${decision.fileId} in source ${source.id}: no longer conflicts")
            val key = IndexedFileKey(fileId = decision.fileId, sourceId = source.id)
            storage.conflictDecisions.remove(key)

            journal.record(
                JournalEvent.ConflictDecisionDropped(
                    sourceId = source.id,
                    path = storage.index.findFile(key)?.path ?: decision.fileId,
                    reason = JournalEvent.ConflictDecisionDropped.Reason.NoLongerConflicts,
                )
            )
        }
    }

    /** [conflicts] are file ids the pass still planned as conflicts. */
    private class PassOutcome(val tally: PassTally, val conflicts: Set<String>)

    private companion object {
        const val MaxRounds = 3

        /** Renewals per call: a link that keeps dropping right after one is not coming back. */
        const val MaxReconnects = 2
    }
}

/** The session to the peer is gone, or could not be opened again. */
private fun Throwable.isLinkLoss(): Boolean = generateSequence(this) { it.cause }.any {
    it is NetworkException.SessionClosed ||
            it is NetworkException.SessionLinkLost ||
            it is NetworkException.Transport ||
            it is NetworkException.NoRoute ||
            it is DeviceUnreachableException
}

private fun PassTally.counting(action: FileAction): PassTally = when (action) {
    is FileAction.Upload -> copy(sent = sent + 1)
    is FileAction.Download -> copy(received = received + 1)
    is FileAction.DeleteLocal -> copy(deletedHere = deletedHere + 1)
    is FileAction.DeleteRemote -> copy(deletedOnPeer = deletedOnPeer + 1)
    is FileAction.MoveLocal, is FileAction.MoveRemote -> copy(moved = moved + 1)
    is FileAction.EvictLocal -> copy(evicted = evicted + 1)
    else -> this
}
