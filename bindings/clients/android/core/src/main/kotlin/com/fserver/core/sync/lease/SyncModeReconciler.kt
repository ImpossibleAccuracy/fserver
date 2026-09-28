package com.fserver.core.sync.lease

import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import timber.log.Timber

/**
 * Both halves must run a source under the same mode before either plans a pass. Settled at the
 * lease: the initiator owns the mode, a follower takes it over, and a different type is never
 * reconciled.
 */
internal class SyncModeReconciler(
    private val storage: FServerStorage,
) {
    sealed interface Verdict {
        data object Agreed : Verdict

        /** The two ends run different mode types. */
        data object TypeMismatch : Verdict

        /** This end is the initiator: the peer takes its mode from us. */
        data object PeerOutdated : Verdict

        /** This end is the follower: it takes the peer's mode. */
        data object AdoptPeer : Verdict
    }

    /** How [source]'s mode here relates to [peerMode], the one the peer runs. */
    fun judge(source: SourceEntry, peerMode: SyncMode): Verdict = when {
        peerMode == source.syncMode -> Verdict.Agreed
        peerMode.type != source.syncMode.type -> Verdict.TypeMismatch
        source.role == SourceEntry.Role.Initiator -> Verdict.PeerOutdated
        else -> Verdict.AdoptPeer
    }

    /** Only the follower takes a mode from the peer, and only settings: never the mode's type. */
    suspend fun adopt(source: SourceEntry, mode: SyncMode): SourceEntry {
        check(judge(source, mode) == Verdict.AdoptPeer) {
            "Source ${source.id}: cannot adopt $mode from ${source.deviceId} as ${source.role} running ${source.syncMode}"
        }

        Timber.i("Source ${source.id}: adopting ${source.deviceId}'s mode $mode")
        val updated = source.copy(syncMode = mode)
        storage.sources.upsert(updated)
        return updated
    }
}
