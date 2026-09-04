package com.fserver.core.sync.setup

import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.files.SourceLocation
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.dto.toDomain
import com.fserver.core.network.dictionary.dto.toDto
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.remote.PeerIndexFetcher
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
    private val peers: PeerIndexFetcher,
    private val timeProvider: TimeProvider,
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
                )
            )
            .getOrThrow()
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

        // Removed here on purpose. Parking it again would ask the user to undo their own decision.
        val tombstone = storage.sources.findTombstone(message.sourceId)
        if (tombstone != null && tombstone.deviceId == peer.deviceId) {
            Timber.i("Device ${peer.deviceId} re-asked for source ${message.sourceId}, removed here")
            answer(
                deviceId = peer.deviceId,
                sourceId = message.sourceId,
                accepted = false,
                reason = RemovedReason,
            )
            return
        }

        storage.sourceRequests.upsert(
            IncomingSourceRequest(
                sourceId = message.sourceId,
                deviceId = peer.deviceId,
                label = message.label,
                syncMode = message.syncMode.toDomain(),
                receivedAt = timeProvider.now(),
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
    ): SourceEntry {
        val request = storage.sourceRequests.findById(sourceId)
            ?: throw IllegalArgumentException("No source request pending for id: $sourceId")

        val source = SourceEntry(
            id = request.sourceId,
            deviceId = request.deviceId,
            location = location,
            syncMode = request.syncMode,
            role = SourceEntry.Role.Follower,
            // Accepting is this side's half of the setup: nothing is left to wait for.
            status = SourceEntry.Status.Active,
            label = request.label,
            createdAt = timeProvider.now(),
        )

        storage.sources.upsert(source)
        storage.sourceRequests.delete(sourceId)

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

        storage.sourceRequests.delete(sourceId)

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
            return
        }

        Timber.i("Device ${peer.deviceId} refused to host source ${message.sourceId}: ${message.reason}")
        storage.sources.updateStatus(
            id = message.sourceId,
            status = SourceEntry.Status.Disabled(message.reason ?: RefusedReason),
        )
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

        private const val RemovedReason = "Source was removed on the other device"

        private const val RefusedReason = "Refused by the other device"
    }
}
