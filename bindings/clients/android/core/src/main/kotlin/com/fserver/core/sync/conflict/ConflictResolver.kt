package com.fserver.core.sync.conflict

import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import com.fserver.core.sync.runner.action.ActionSteps
import com.fserver.files.upload.FileAction
import com.fserver.files.upload.FileRecord
import timber.log.Timber

/**
 * Carries out a planned [FileAction.Conflict] the way the source's mode says: last write wins, or
 * whatever the user decided, once they did.
 */
internal class ConflictResolver(
    private val storage: FServerStorage,
    private val steps: ActionSteps,
    private val copier: ConflictCopier,
) {
    suspend fun resolve(action: FileAction.Conflict, source: SourceEntry) {
        val resolution = (source.syncMode as? SyncMode.Mirror)?.conflictResolution
            ?: SyncMode.Mirror.ConflictResolution.LastWriteWins

        when (resolution) {
            SyncMode.Mirror.ConflictResolution.LastWriteWins ->
                transferWinner(action, source, localWins = lastWriteWins(action, source))

            SyncMode.Mirror.ConflictResolution.Ask -> applyDecision(action, source)
        }
    }

    private suspend fun lastWriteWins(action: FileAction.Conflict, source: SourceEntry): Boolean {
        val local = action.local.metadata.version
        val remote = action.remote.metadata.version

        return when {
            // A side without bytes cannot win
            action.local.state is FileRecord.State.Evicted -> false
            action.remote.state is FileRecord.State.Evicted -> true
            // If both sides have the same version, the device with the higher ID wins
            local == remote -> storage.identity.localDevice().deviceId > source.deviceId
            local == null -> false // No local version, remote wins
            remote == null -> true // Remote has no version, local wins
            else -> local.compareHlc(remote) // Last Write Wins based on HLC comparison
        }
    }

    /**
     * The conflict stays held until the user decides. A decision made over versions that have
     * changed since is dropped: the user never saw what it would now overwrite.
     */
    private suspend fun applyDecision(action: FileAction.Conflict, source: SourceEntry) {
        val key = IndexedFileKey(fileId = action.id.value, sourceId = source.id)
        val decision = storage.conflictDecisions.find(key) ?: return

        if (decision.local != action.local.seenVersion() || decision.remote != action.remote.seenVersion()) {
            // TODO: history entry - "your choice on <file> was dropped: it changed since".
            Timber.i("Dropping decision on ${action.local.path} in source ${source.id}: a side changed since")
            storage.conflictDecisions.remove(key)
            return
        }

        // Eviction is not a version, so the check above misses a side evicted since the decision.
        if (decision.choice !in action.choices()) {
            Timber.i("Dropping decision on ${action.local.path} in source ${source.id}: ${decision.choice} no longer available")
            storage.conflictDecisions.remove(key)
            return
        }

        when (decision.choice) {
            ConflictDecision.Choice.KeepLocal -> transferWinner(action, source, localWins = true)

            ConflictDecision.Choice.KeepRemote -> transferWinner(action, source, localWins = false)

            ConflictDecision.Choice.KeepBoth -> {
                copier.copyAside(action.local, source)
                // The copy is made: a retry after a failed transfer must not make another one.
                storage.conflictDecisions.put(decision.copy(choice = ConflictDecision.Choice.KeepRemote))
                transferWinner(action, source, localWins = false)
            }
        }

        storage.conflictDecisions.remove(key)
    }

    /**
     * Moves the winner's side over the loser's under the merged version; the winner's side adopts
     * it here, so both agree now rather than on the next pass.
     */
    private suspend fun transferWinner(
        action: FileAction.Conflict,
        source: SourceEntry,
        localWins: Boolean,
    ) {
        val local = action.local.metadata.version
        val remote = action.remote.metadata.version
        val merged = local?.merge(remote) ?: remote

        val winner = if (localWins) action.local else action.remote
        val loser = if (localWins) action.remote else action.local

        when (winner.state) {
            is FileRecord.State.Present -> if (localWins) {
                val sent = steps.upload(source, action.local, merged)
                steps.adoptLocally(source, action.local, merged, expected = sent)
            } else {
                steps.download(source, action.remote, merged)

                // An unhashed remote was hashed on the way: our copy holds its bytes now.
                val key = IndexedFileKey(fileId = action.id.value, sourceId = source.id)
                val received = action.remote.content ?: storage.index.findFile(key)?.hash
                if (received != null) steps.adoptRemotely(source, action.remote, merged, expected = received)
            }

            is FileRecord.State.Deleted -> if (loser.state !is FileRecord.State.Deleted) {
                if (localWins) {
                    steps.deleteRemote(source, action.remote, merged)
                    steps.adoptLocally(source, action.local, merged, expected = null)
                } else {
                    steps.deleteLocal(source, action.local, merged)
                    steps.adoptRemotely(source, action.remote, merged, expected = null)
                }
            }

            // Filtered out by lastWriteWins and choices(); a side without bytes has nothing to send.
            is FileRecord.State.Evicted ->
                Timber.w("Conflict resolution: evicted ${winner.path} cannot win over ${loser.path}")
        }
    }
}

/** [FileConflict.choices], over plan records. */
internal fun FileAction.Conflict.choices(): Set<ConflictDecision.Choice> = conflictChoices(
    localEvicted = local.state is FileRecord.State.Evicted,
    remoteEvicted = remote.state is FileRecord.State.Evicted,
    bothPresent = local.state is FileRecord.State.Present && remote.state is FileRecord.State.Present,
)
