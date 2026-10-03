package com.fserver.core.journal.impl

import com.fserver.common.exception.FileSystemException
import com.fserver.common.exception.NetworkException
import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.journal.JournalEvent
import com.fserver.core.journal.PassTally
import com.fserver.core.network.PeerIdentityMismatchException
import com.fserver.core.network.dictionary.FileServerDictionary
import com.fserver.core.oneshot.model.OneShotTransfer
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.progress.toSyncFailure
import com.fserver.core.util.TimeProvider
import com.fserver.net.DictionaryMismatchException
import timber.log.Timber

/**
 * The engine's side of the journal: what to record, and when an issue is gone. Never throws - a
 * journal that cannot be written must not take the action it describes down with it.
 */
internal class JournalWriter(
    private val storage: FServerStorage,
    private val timeProvider: TimeProvider,
) {
    private val store get() = storage.journal

    suspend fun record(event: JournalEvent) = write {
        store.append(event, timeProvider.now())
        store.trim(MaxEntries)
    }

    suspend fun raise(issue: JournalEvent.Issue) = write {
        store.raise(issue, timeProvider.now())
        store.trim(MaxEntries)
    }

    suspend fun solve(key: String) = write { store.solve(key, timeProvider.now()) }

    /** The source is gone: nothing about it can be fixed any more. */
    suspend fun solveForSource(sourceId: String) = write { store.solveForSource(sourceId, timeProvider.now()) }

    /** The device is forgotten: nothing about it can be fixed any more. */
    suspend fun solveForDevice(deviceId: String) = write { store.solveForDevice(deviceId, timeProvider.now()) }

    /**
     * A pass over [source] got through. [conflicts] are the files it still planned as conflicts:
     * a held conflict outside them was settled some other way.
     */
    suspend fun passSucceeded(source: SourceEntry, tally: PassTally, conflicts: Set<String>) {
        if (!tally.isEmpty) record(JournalEvent.PassCompleted(source.id, source.deviceId, tally))

        solve(JournalEvent.PassFailed.keyOf(source.id))
        solve(JournalEvent.SealedFilesUnreadable.keyOf(source.id))

        write {
            store.openIssues(source.id)
                .mapNotNull { it.event as? JournalEvent.ConflictHeld }
                .filter { it.fileId !in conflicts }
                .forEach { store.solve(it.key, timeProvider.now()) }
        }
    }

    suspend fun passFailed(source: SourceEntry, failure: Throwable) {
        raise(JournalEvent.PassFailed(source.id, source.deviceId, failure.toSyncFailure().reason))
        failure.sealedProblem()?.let { raise(JournalEvent.SealedFilesUnreadable(source.id, it)) }
    }

    /** A fresh dial to [deviceId] went through. */
    suspend fun connected(deviceId: String) {
        solve(JournalEvent.IncompatibleDictionary.keyOf(deviceId))
        solve(JournalEvent.ConnectionRefused.keyOf(deviceId))
    }

    /** A dial to [deviceId] failed. Only a peer that answered and disagreed is an issue; silence is not. */
    suspend fun connectFailed(deviceId: String, failure: Throwable) {
        val causes = generateSequence(failure) { it.cause }

        causes.firstNotNullOfOrNull { it as? DictionaryMismatchException }?.let {
            raise(JournalEvent.IncompatibleDictionary(deviceId, FileServerDictionary.Version, it.remote.version))
            return
        }

        val reason = causes.firstNotNullOfOrNull {
            when (it) {
                is PeerIdentityMismatchException -> JournalEvent.ConnectionRefused.Reason.IdentityMismatch
                is NetworkException.AuthenticationRejected -> JournalEvent.ConnectionRefused.Reason.AuthenticationRejected
                is NetworkException.Handshake -> JournalEvent.ConnectionRefused.Reason.HandshakeFailed
                else -> null
            }
        } ?: return

        raise(JournalEvent.ConnectionRefused(deviceId, reason))
    }

    suspend fun clockMeasured(deviceId: String, offsetMs: Long, skewed: Boolean) {
        if (skewed) {
            raise(JournalEvent.ClockSkewed(deviceId, offsetMs))
        } else {
            solve(JournalEvent.ClockSkewed.keyOf(deviceId))
        }
    }

    /** Records [transferId] if it is finished. Call after every status write that may have finished it. */
    suspend fun oneShotSettled(transferId: String) = write {
        val transfer = storage.oneShotTransfers.find(transferId) ?: return@write
        val outcome = when (transfer.status) {
            OneShotTransfer.Status.Completed -> JournalEvent.OneShotFinished.Outcome.Completed
            OneShotTransfer.Status.Declined -> JournalEvent.OneShotFinished.Outcome.Declined
            OneShotTransfer.Status.Cancelled -> JournalEvent.OneShotFinished.Outcome.Cancelled
            is OneShotTransfer.Status.Failed -> JournalEvent.OneShotFinished.Outcome.Failed
            OneShotTransfer.Status.Pending, OneShotTransfer.Status.Active -> return@write
        }

        store.append(
            JournalEvent.OneShotFinished(
                transferId = transfer.id,
                deviceId = transfer.peer.deviceId,
                peerName = transfer.peer.displayName,
                direction = when (transfer.direction) {
                    is OneShotTransfer.Direction.Outgoing -> JournalEvent.OneShotFinished.Direction.Outgoing
                    is OneShotTransfer.Direction.Incoming -> JournalEvent.OneShotFinished.Direction.Incoming
                },
                outcome = outcome,
                files = transfer.files.size,
            ),
            transfer.finishedAt ?: timeProvider.now(),
        )
        store.trim(MaxEntries)
    }

    private suspend inline fun write(block: () -> Unit) {
        runCatchingCancellable(block).onFailure { Timber.w(it, "Could not write to the activity journal") }
    }

    private companion object {
        /** Open issues are kept on top of these, whatever their age. */
        const val MaxEntries = 1000
    }
}

/** Why sealed files would not open, looking through the actions a failed pass collected. */
private fun Throwable.sealedProblem(): JournalEvent.SealedFilesUnreadable.Problem? =
    when (this) {
        is FileSystemException.MissingKey -> JournalEvent.SealedFilesUnreadable.Problem.MissingKey
        is FileSystemException.UnknownCipher -> JournalEvent.SealedFilesUnreadable.Problem.UnknownCipher
        is FileSystemException.Corrupted -> JournalEvent.SealedFilesUnreadable.Problem.Corrupted
        else -> suppressedExceptions.firstNotNullOfOrNull { it.sealedProblem() } ?: cause?.sealedProblem()
    }
