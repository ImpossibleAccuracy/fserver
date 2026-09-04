package com.fserver.core.sync.server

import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.model.SourceEntry
import com.fserver.net.security.identity.PeerIdentity
import timber.log.Timber

/** What a peer-chosen source id resolves to for the peer that named it. See [SourceAuthorizer]. */
internal sealed interface ResolvedIncomingSource {
    data class Servable(val source: SourceEntry) : ResolvedIncomingSource

    /** Ours, and gone for good: dropped or disabled here. The peer should stop asking. */
    data class Gone(val reason: String) : ResolvedIncomingSource

    /** No such source, or not this peer's. Deliberately the same answer for both. */
    data object Unknown : ResolvedIncomingSource
}

/**
 * The whole access control on the answering side.
 *
 * Every id served to a peer was chosen by that peer, so without this check any authenticated device
 * can list, delete or overwrite any source on this one. Every handler here resolves through this
 * rather than reaching [FServerStorage.sources] directly.
 */
internal class SourceAuthorizer(
    private val storage: FServerStorage,
) {
    /** [resolve], for the entry points that have nothing to say to a peer but an error. */
    suspend fun authorizedSource(peer: PeerIdentity, sourceId: String): SourceEntry =
        when (val resolved = resolve(peer, sourceId)) {
            is ResolvedIncomingSource.Servable -> resolved.source

            is ResolvedIncomingSource.Gone ->
                throw IllegalArgumentException("Source $sourceId is no longer synced: ${resolved.reason}")

            ResolvedIncomingSource.Unknown -> throw IllegalArgumentException("Source $sourceId not found")
        }

    /**
     * What [sourceId] is, as far as [peer] is concerned.
     *
     * [ResolvedIncomingSource.Unknown] covers a miss and someone else's source alike on purpose - whether a
     * source exists is not something an unrelated peer gets to learn, which is also why only the
     * paired device is ever told [ResolvedIncomingSource.Gone] or [ResolvedIncomingSource.Servable].
     */
    suspend fun resolve(peer: PeerIdentity, sourceId: String): ResolvedIncomingSource {
        val source = storage.sources.findById(sourceId)

        if (source != null) {
            if (source.deviceId != peer.deviceId) {
                Timber.w("Device ${peer.deviceId} asked for source $sourceId, which syncs with ${source.deviceId}")
                return ResolvedIncomingSource.Unknown
            }

            return when (val status = source.status) {
                SourceEntry.Status.Active -> ResolvedIncomingSource.Servable(source)

                is SourceEntry.Status.Disabled -> ResolvedIncomingSource.Gone(status.reason)

                // The peer would not be asking about a source it had not registered, so its
                // acceptance landed in its own store even though the answer never reached ours.
                // Serving it while still calling it pending is what would strand the pair.
                SourceEntry.Status.Pending -> {
                    Timber.i("Source $sourceId activated: ${peer.deviceId} is asking for it")
                    storage.sources.updateStatus(sourceId, SourceEntry.Status.Active)
                    ResolvedIncomingSource.Servable(source.copy(status = SourceEntry.Status.Active))
                }
            }
        }

        val tombstone = storage.sources.findTombstone(sourceId)

        return if (tombstone != null && tombstone.deviceId == peer.deviceId) {
            ResolvedIncomingSource.Gone(RemovedReason)
        } else {
            ResolvedIncomingSource.Unknown
        }
    }

    private companion object {
        const val RemovedReason = "Source was removed on the other device"
    }
}
