package com.fserver.core.sync.lease

import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.remote.PeerIndexFetcher
import com.fserver.net.session.PeerSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * The asking half of the sync lease: agrees with the peer on who runs the pass over a source.
 *
 * Both devices can start a pass at the same moment - a timer here, a file change there - and each
 * plans from a snapshot of both indexes, so two at once plan against state the other is already
 * rewriting. The pass that holds the lease runs; the other skips the source, because the pass that
 * did run is bidirectional and covers what the skipped one would have done.
 */
internal class SyncLeaseNegotiator(
    private val storage: FServerStorage,
    private val registry: SyncLeaseRegistry,
    private val peers: PeerIndexFetcher,
) {
    /** Runs [block] only if both devices agree we hold [source]. Skips - never queues - otherwise. */
    suspend fun runWithLease(source: SourceEntry, block: suspend () -> Unit) {
        val lease = acquire(source) ?: return

        try {
            block()
        } finally {
            withContext(NonCancellable) { release(source, lease) }
        }
    }

    /** @return the held lease id, or null when the peer or a local pass already has the source. */
    private suspend fun acquire(source: SourceEntry): Lease? {
        val leaseId = registry.beginAcquire(source.id)
        if (leaseId == null) {
            Timber.i("Source ${source.id} skipped: another pass already holds it")
            return null
        }

        val session = try {
            peers.connectToDevice(source)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            registry.release(source.id, leaseId)
            throw e
        }

        val response = try {
            session.request(
                FileServerMessages.AcquireSyncLease.Request(
                    sourceId = source.id,
                    leaseId = leaseId,
                )
            ).getOrThrow()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            registry.release(source.id, leaseId)
            throw e
        }

        return when (response) {
            is FileServerMessages.AcquireSyncLease.Granted -> {
                // The peer granted us the source, but a request of its own may have taken it over
                // here in the meantime - it wins ties on device id, and it is already running.
                if (registry.confirmLocal(source.id, leaseId)) {
                    Lease(session, leaseId)
                } else {
                    Timber.i("Source ${source.id} skipped: ${source.deviceId} claimed it first")
                    giveBack(session, source.id, leaseId)
                    null
                }
            }

            is FileServerMessages.AcquireSyncLease.Denied -> {
                registry.release(source.id, leaseId)
                Timber.i("Source ${source.id} skipped: ${source.deviceId} is syncing it (${response.reason})")
                null
            }

            // The peer dropped it's half.
            // Disable source here, so user can re-enable it if they want to continue syncing with the peer.
            is FileServerMessages.AcquireSyncLease.Inactive -> {
                registry.release(source.id, leaseId)
                Timber.i("Source ${source.id} disabled: ${source.deviceId} no longer syncs it (${response.reason})")
                storage.sources.updateStatus(
                    id = source.id,
                    status = SourceEntry.Status.Disabled(response.reason)
                )
                null
            }

            else -> {
                registry.release(source.id, leaseId)
                throw IllegalStateException("Unexpected answer to AcquireSyncLease from ${source.deviceId}: $response")
            }
        }
    }

    private suspend fun release(source: SourceEntry, lease: Lease) {
        registry.release(source.id, lease.id)
        giveBack(lease.session, source.id, lease.id)
    }

    /**
     * Hands the lease back over the session that granted it. Best effort on purpose: the peer's
     * TTL and its own session bookkeeping free the source anyway, and a pass must not fail because
     * the link died on the way out.
     */
    private suspend fun giveBack(
        session: PeerSession<FileServerMessages>,
        sourceId: String,
        leaseId: String,
    ) {
        session.send(FileServerMessages.AcquireSyncLease.ReleaseLease(sourceId, leaseId))
            .exceptionOrNull()
            ?.let { Timber.w(it, "Could not release the lease on source $sourceId") }
    }

    /** Held over the session it was granted on: that is where the release has to go back. */
    private class Lease(
        val session: PeerSession<FileServerMessages>,
        val id: String,
    )
}