package com.fserver.core.sync.server.handler

import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.files.gc.GarbageCollector
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.dto.toDomain
import com.fserver.core.network.dictionary.dto.toDto
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import com.fserver.core.sync.lease.SyncLeaseRegistry
import com.fserver.core.sync.server.ResolvedIncomingSource
import com.fserver.core.sync.server.SourceAuthorizer
import com.fserver.core.util.TimeProvider
import com.fserver.net.security.identity.PeerIdentity
import com.fserver.net.session.PeerSession
import timber.log.Timber

/** Answers the peer's bid to run the pass over one source, and takes the lease back after. */
internal class SyncLeaseHandler(
    private val authorizer: SourceAuthorizer,
    private val storage: FServerStorage,
    private val leaseRegistry: SyncLeaseRegistry,
    private val garbageCollector: GarbageCollector,
    private val timeProvider: TimeProvider,
) {
    suspend fun answer(
        event: PeerSession.Inbound<FileServerMessages>,
        message: FileServerMessages.AcquireSyncLease.Request,
        session: PeerSession<FileServerMessages>,
    ) {
        val reply = event.reply
        if (reply == null) {
            Timber.w("Cannot answer AcquireSyncLease(${message.sourceId}) from ${session.identity.deviceId}: no reply channel")
            return
        }

        // Resolved rather than authorized: a source this device dropped is answered, not refused,
        // so the peer disables it's half instead of asking again on every pass.
        val resolved = authorizer.resolve(session.identity, message.sourceId)

        if (resolved !is ResolvedIncomingSource.Servable) {
            reply(resolved.toRefusal(message.sourceId))
            return
        }

        val source = resolved.source
        val requested = message.syncMode.toDomain()

        modeRefusal(source, requested)?.let {
            reply(it)
            return
        }

        val granted = runCatchingCancellable {
            leaseRegistry.grantToPeer(
                sourceId = message.sourceId,
                peerDeviceId = session.identity.deviceId,
                leaseId = message.leaseId,
                localDeviceId = storage.identity.localDevice().deviceId,
            )
        }

        granted.fold(
            onSuccess = { held ->
                if (held) {
                    // Adopted only once granted: a denial means a pass here still runs the old mode.
                    if (requested != source.syncMode) adoptMode(source, requested)

                    Timber.i("Granted sync lease for source ${message.sourceId} to ${session.identity.deviceId}")
                    reply(
                        FileServerMessages.AcquireSyncLease.Granted(
                            sourceId = message.sourceId,
                            leaseId = message.leaseId,
                        )
                    )
                } else {
                    Timber.i("Denied sync lease for source ${message.sourceId} to ${session.identity.deviceId}")
                    reply(
                        FileServerMessages.AcquireSyncLease.Denied(
                            sourceId = message.sourceId,
                            reason = SyncingHereReason,
                        )
                    )
                }
            },
            onFailure = { t ->
                Timber.w(
                    t,
                    "Cannot lease source ${message.sourceId} to ${session.identity.deviceId}"
                )

                reply(
                    FileServerMessages.AcquireSyncLease.Denied(
                        sourceId = message.sourceId,
                        reason = t.message ?: "Unknown error",
                    )
                )
            },
        )
    }

    suspend fun release(
        message: FileServerMessages.AcquireSyncLease.ReleaseLease,
        peer: PeerIdentity,
    ) {
        val released = leaseRegistry.releaseFromPeer(
            sourceId = message.sourceId,
            peerDeviceId = peer.deviceId,
            leaseId = message.leaseId,
            failure = message.failure,
        )

        if (released && message.failure == null) {
            storage.sources.markSynced(message.sourceId, timeProvider.now())
        }

        garbageCollector.collectGarbageAsync()
    }

    /**
     * Both halves must run [source] under the same mode before either plans a pass. The initiator
     * owns the mode: a follower here takes [requested] once granted, an initiator here sends its own
     * back. A different mode type is never reconciled.
     */
    private fun modeRefusal(source: SourceEntry, requested: SyncMode): FileServerMessages.AcquireSyncLease? =
        when {
            requested == source.syncMode -> null

            requested.type != source.syncMode.type -> {
                Timber.w("Source ${source.id}: ${source.deviceId} runs ${requested.type}, this device ${source.syncMode.type}")
                FileServerMessages.AcquireSyncLease.Denied(
                    sourceId = source.id,
                    reason = ModeTypeMismatchReason,
                )
            }

            source.role == SourceEntry.Role.Initiator -> FileServerMessages.AcquireSyncLease.Outdated(
                sourceId = source.id,
                syncMode = source.syncMode.toDto(),
            )

            else -> null
        }

    private suspend fun adoptMode(source: SourceEntry, mode: SyncMode) {
        Timber.i("Source ${source.id}: adopting ${source.deviceId}'s mode $mode")
        storage.sources.upsert(source.copy(syncMode = mode))
    }

    /** How a refusal reaches the peer: [ResolvedIncomingSource.Gone] is final, everything else is "not now". */
    private fun ResolvedIncomingSource.toRefusal(sourceId: String): FileServerMessages.AcquireSyncLease =
        when (this) {
            is ResolvedIncomingSource.Gone -> FileServerMessages.AcquireSyncLease.Inactive(
                sourceId = sourceId,
                reason = reason,
            )

            else -> FileServerMessages.AcquireSyncLease.Denied(
                sourceId = sourceId,
                reason = UnknownSourceReason,
            )
        }

    private companion object {
        const val SyncingHereReason = "Source is being synced by its other device"
        const val UnknownSourceReason = "Source not found"
        const val ModeTypeMismatchReason = "Devices run the source under different modes"
    }
}
