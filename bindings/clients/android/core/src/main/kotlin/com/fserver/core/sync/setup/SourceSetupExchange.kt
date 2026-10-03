package com.fserver.core.sync.setup

import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.files.SourceLocation
import com.fserver.core.journal.JournalEvent
import com.fserver.core.journal.impl.JournalWriter
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.dto.toDomain
import com.fserver.core.network.dictionary.dto.toDto
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.metadata.PeerMetadataExchange
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SourceRemovedReason
import com.fserver.core.sync.model.receivesMetadata
import com.fserver.core.sync.remote.PeerConnector
import com.fserver.core.util.TimeProvider
import com.fserver.net.security.identity.PeerIdentity
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

/**
 * Pairing a source across the two devices that sync it - both halves of the exchange.
 *
 * A source only works when both devices hold a record under the same id: `SourceAuthorizer`
 * refuses an id it does not know, so a source registered on one side alone fails every pass. The
 * asking device sends what it registered; the answering one parks the ask until its user has seen
 * it, then registers it's half into app-private storage.
 */
internal class SourceSetupExchange(
    private val storage: FServerStorage,
    private val peers: PeerConnector,
    private val timeProvider: TimeProvider,
    private val metadata: PeerMetadataExchange,
    private val journal: JournalWriter,
) {
    /** Every ask waiting on this device's user, oldest first. */
    val pending: Flow<List<IncomingSourceRequest>> = storage.sourceRequests.pending()

    /** Asks [source]'s peer to register the other half. Throws if it could not be delivered. */
    suspend fun requestRemote(source: SourceEntry) {
        peers.connectToDevice(source.deviceId)
            .send(
                FileServerMessages.ConfigureSource.Request(
                    sourceId = source.id,
                    label = source.label,
                    syncMode = source.syncMode.toDto(),
                    metadata = metadata.describe(source),
                )
            )
            .getOrThrow()
    }

    /**
     * Re-asks the peer of every source in [sources] still pending here. It may have accepted while
     * we were offline, and a peer that already answered answers again instead of asking its user twice.
     */
    suspend fun resendPending(sources: List<SourceEntry>) {
        val pending = sources.filter {
            it.role == SourceEntry.Role.Initiator && it.status == SourceEntry.Status.Pending
        }

        for (source in pending) {
            val current = storage.sources.findById(source.id)
                ?.takeIf { it.status == SourceEntry.Status.Pending }
                ?: continue

            runCatchingCancellable { requestRemote(current) }
                .exceptionOrNull()
                ?.let {
                    Timber.w(
                        it,
                        "Could not re-ask ${current.deviceId} to host source ${current.id}"
                    )
                }
        }
    }

    /**
     * Parks what [peer] asked us to host.
     *
     * Something already settled is answered again rather than shown to the user a second time: the
     * peer only re-sends because our answer never arrived.
     */
    suspend fun onRequest(peer: PeerIdentity, message: FileServerMessages.ConfigureSource.Request) {
        val existing = storage.sources.findById(message.sourceId)
        if (existing != null) {
            if (existing.deviceId != peer.deviceId) {
                Timber.w("Device ${peer.deviceId} asked to host source ${message.sourceId}, which syncs with ${existing.deviceId}")
                return
            }

            val disabled = existing.status as? SourceEntry.Status.Disabled

            Timber.i("Device ${peer.deviceId} re-asked for source ${message.sourceId}, already settled here")
            answer(
                deviceId = peer.deviceId,
                sourceId = message.sourceId,
                accepted = disabled == null,
                reason = disabled?.reason,
            )
            return
        }

        // Removed or refused here on purpose. Parking it again would ask the user to undo their own
        // decision.
        val tombstone = storage.sources.findTombstone(message.sourceId)
        if (tombstone != null && tombstone.deviceId == peer.deviceId) {
            Timber.i("Device ${peer.deviceId} re-asked for source ${message.sourceId}, settled here")
            answer(
                deviceId = peer.deviceId,
                sourceId = message.sourceId,
                accepted = false,
                reason = if (tombstone.location == null) RejectedReason else SourceRemovedReason,
            )
            return
        }

        // A re-ask refreshes what is parked, but keeps its place in the queue.
        val parked = storage.sourceRequests.findById(message.sourceId)
            ?.takeIf { it.deviceId == peer.deviceId }
        val now = timeProvider.now()

        if (parked == null) {
            journal.record(
                JournalEvent.SourceRequested(
                    sourceId = message.sourceId,
                    deviceId = peer.deviceId,
                    label = message.label,
                    mode = message.syncMode.toDomain().type,
                )
            )
        }

        storage.sourceRequests.upsert(
            IncomingSourceRequest(
                sourceId = message.sourceId,
                deviceId = peer.deviceId,
                label = message.label,
                metadata = message.metadata.toDomain(
                    sourceId = message.sourceId,
                    deviceId = peer.deviceId,
                    updatedAt = now
                ),
                syncMode = message.syncMode.toDomain(),
                receivedAt = parked?.receivedAt ?: now,
            )
        )
    }

    /**
     * Registers our half of [sourceId] into [location] and tells the peer.
     *
     * The peer's id is reused as-is: both devices address the source by it, and the files arriving
     * under [location] are laid out by the peer's canonical paths, so the id is also what keeps
     * two hosted sources from writing over each other. Its [com.fserver.core.sync.model.SyncMode] is
     * carried over unchanged - the mode describes the source, not one end of it, and
     * [SourceEntry.Role.Follower] is what says which end this device is.
     */
    suspend fun accept(
        sourceId: String,
        location: SourceLocation.Hostable,
        preferences: SourceEntry.Preferences = SourceEntry.Preferences.Default,
    ): SourceEntry {
        val request = storage.sourceRequests.findById(sourceId)
            ?: throw IllegalArgumentException("No source request pending for id: $sourceId")

        val source = SourceEntry(
            id = request.sourceId,
            deviceId = request.deviceId,
            location = location,
            syncMode = request.syncMode,
            preferences = preferences,
            role = SourceEntry.Role.Follower,
            // Accepting is this side's half of the setup: nothing is left to wait for.
            status = SourceEntry.Status.Active,
            label = request.label,
            createdAt = timeProvider.now(),
        )

        storage.sources.upsert(source)
        storage.sourceRequests.delete(sourceId)

        // Kept only where the peer goes on reporting: a one-way follower would hold a snapshot
        // that never updates.
        if (source.receivesMetadata) metadata.record(request.metadata)
        metadata.refresh(source)

        answer(
            deviceId = request.deviceId,
            sourceId = sourceId,
            accepted = true,
            reason = null,
        )

        return source
    }

    /** Drops [sourceId] and tells the peer, so it can drop its own half rather than retry forever. */
    suspend fun reject(sourceId: String, reason: String = RejectedReason) {
        val request = storage.sourceRequests.findById(sourceId)
            ?: throw IllegalArgumentException("No source request pending for id: $sourceId")

        // Remembered, so a re-ask from a peer that missed this answer is refused again, not re-parked.
        storage.sources.recordRefusal(sourceId, request.deviceId)
        storage.sourceRequests.delete(sourceId)
        journal.record(
            JournalEvent.SourceRequestRejected(
                sourceId = sourceId,
                deviceId = request.deviceId,
                label = request.label
            )
        )

        answer(
            deviceId = request.deviceId,
            sourceId = sourceId,
            accepted = false,
            reason = reason,
        )
    }

    /**
     * The peer's verdict on a source we registered - what moves it off [SourceEntry.Status.Pending].
     *
     * A refusal disables the source rather than dropping it: the record is what keeps the next
     * pass from asking again, and what the user reads to find out why it never started.
     */
    suspend fun onDecision(
        peer: PeerIdentity,
        message: FileServerMessages.ConfigureSource.Decision,
    ) {
        val source = storage.sources.findById(message.sourceId)

        if (source == null) {
            // TODO: peer answered for a source, but out side deleted/disabled it
            Timber.w("Device ${peer.deviceId} answered for unknown source ${message.sourceId}")
            return
        }

        // Same check as everything else served from a peer-chosen id: only the device a source
        // syncs with gets to decide anything about it.
        if (source.deviceId != peer.deviceId) {
            Timber.w("Device ${peer.deviceId} answered for source ${message.sourceId}, which syncs with ${source.deviceId}")
            return
        }

        if (message.accepted) {
            storage.sources.updateStatus(message.sourceId, SourceEntry.Status.Active)
            if (source.status == SourceEntry.Status.Pending) {
                journal.record(
                    JournalEvent.SourceAcceptedByPeer(
                        sourceId = source.id,
                        deviceId = source.deviceId,
                        label = source.label
                    )
                )
            }
            return
        }

        Timber.i("Device ${peer.deviceId} refused to host source ${message.sourceId}: ${message.reason}")
        storage.sources.updateStatus(
            id = message.sourceId,
            status = SourceEntry.Status.Disabled(message.reason ?: RefusedReason),
        )
        if (source.status !is SourceEntry.Status.Disabled) {
            journal.record(
                JournalEvent.SourceDisabledByPeer(
                    sourceId = source.id,
                    deviceId = source.deviceId,
                    label = source.label,
                    reason = JournalEvent.SourceDisabledByPeer.Reason.Refused,
                )
            )
        }
    }

    /**
     * Best effort on purpose: the answer is already recorded here, and a link that died on the way
     * out must not leave the user's accept looking like it failed. The peer re-asks otherwise.
     */
    private suspend fun answer(
        deviceId: String,
        sourceId: String,
        accepted: Boolean,
        reason: String?,
    ) {
        runCatchingCancellable {
            peers.connectToDevice(deviceId)
                .send(
                    FileServerMessages.ConfigureSource.Decision(
                        sourceId = sourceId,
                        accepted = accepted,
                        reason = reason,
                    )
                )
                .getOrThrow()
        }.exceptionOrNull()?.let {
            Timber.w(it, "Could not tell $deviceId our answer on source $sourceId")
        }
    }

    companion object {
        private const val RejectedReason = "Rejected by user"

        private const val RefusedReason = "Refused by the other device"
    }
}
