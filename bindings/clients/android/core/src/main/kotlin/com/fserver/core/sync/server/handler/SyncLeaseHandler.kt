package com.fserver.core.sync.server.handler

import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.dto.toDomain
import com.fserver.core.network.dictionary.dto.toDto
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.lease.SyncLeaseRegistry
import com.fserver.core.sync.lease.SyncModeReconciler
import com.fserver.core.sync.model.peerDrivesSync
import com.fserver.core.sync.runner.pass.PassCompletion
import com.fserver.core.sync.server.ResolvedIncomingSource
import com.fserver.core.sync.server.SourceAuthorizer
import com.fserver.net.security.identity.PeerIdentity
import com.fserver.net.session.PeerSession
import timber.log.Timber

/** Answers the peer's bid to run the pass over one source, and takes the lease back after. */
internal class SyncLeaseHandler(
    private val authorizer: SourceAuthorizer,
    private val storage: FServerStorage,
    private val leaseRegistry: SyncLeaseRegistry,
    private val modes: SyncModeReconciler,
    private val completion: PassCompletion,
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

        val verdict = modes.judge(source, requested)

        when (verdict) {
            SyncModeReconciler.Verdict.TypeMismatch -> {
                Timber.w("Source ${source.id}: ${source.deviceId} runs ${requested.type}, this device ${source.syncMode.type}")
                reply(FileServerMessages.AcquireSyncLease.Denied(sourceId = source.id, reason = ModeTypeMismatchReason))
                return
            }

            SyncModeReconciler.Verdict.PeerOutdated -> {
                reply(FileServerMessages.AcquireSyncLease.Outdated(sourceId = source.id, syncMode = source.syncMode.toDto()))
                return
            }

            SyncModeReconciler.Verdict.Agreed, SyncModeReconciler.Verdict.AdoptPeer -> Unit
        }

        if (!source.peerDrivesSync) {
            Timber.w("Source ${source.id}: ${source.deviceId} asked to drive ${source.syncMode.type}, which runs from here")
            reply(FileServerMessages.AcquireSyncLease.Denied(sourceId = source.id, reason = DrivenHereReason))
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
                    if (verdict == SyncModeReconciler.Verdict.AdoptPeer) modes.adopt(source, requested)

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

        completion.peerPassEnded(message.sourceId, succeeded = released && message.failure == null)
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
        const val DrivenHereReason = "Source is synced from its initiator only"
    }
}
