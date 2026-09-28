package com.fserver.core.sync.lease

import com.fserver.common.utils.IdGenerator
import com.fserver.core.sync.progress.SourcePass
import com.fserver.core.sync.progress.SyncFailureReason
import com.fserver.core.sync.progress.impl.SyncProgressReporter
import com.fserver.core.util.TimeProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Who is allowed to run a pass over a source right now - this device or the peer it syncs with.
 *
 * A pass plans from a snapshot of both indexes, so two of them running at once plan against state
 * the other is already changing. One registry serves both halves of the protocol: the pass asks
 * [beginAcquire] before it talks to the peer, and `SyncLeaseHandler` asks [grantToPeer] when the
 * peer asks us for the same source.
 *
 * Nothing here is persisted: a lease dies with the process holding it, which is what a restart
 * should mean.
 */
internal class SyncLeaseRegistry(
    private val timeProvider: TimeProvider,
    private val progress: SyncProgressReporter,
) {
    private val lock = Mutex()

    /** Keyed by source id. Absent = nobody holds the source. */
    private val leases = HashMap<String, Lease>()

    /**
     * Claims [sourceId] for a local pass that is about to ask the peer for the same.
     *
     * @return the id to send in `AcquireSyncLease`, or null when the source is already taken.
     */
    suspend fun beginAcquire(sourceId: String): String? = lock.withLock {
        if (holderOf(sourceId) != null) return@withLock null

        val leaseId = IdGenerator.nextId
        leases[sourceId] = Lease.Acquiring(leaseId)
        leaseId
    }

    /**
     * Turns the claim [beginAcquire] made into a running pass, once the peer has granted its side.
     *
     * @return false when the claim is gone - a peer with a winning device id took the source over
     * while we were waiting. The caller must then hand the peer's own grant back and skip.
     */
    suspend fun confirmLocal(sourceId: String, leaseId: String): Boolean = lock.withLock {
        val current = leases[sourceId]
        if (current !is Lease.Acquiring || current.leaseId != leaseId) return@withLock false

        leases[sourceId] = Lease.Local(leaseId)
        true
    }

    /** Drops a local claim or pass. A [leaseId] that no longer holds the source does nothing. */
    suspend fun release(sourceId: String, leaseId: String) = lock.withLock {
        val current = leases[sourceId]

        val ours = when (current) {
            is Lease.Acquiring -> current.leaseId == leaseId
            is Lease.Local -> current.leaseId == leaseId
            else -> false
        }

        if (ours) leases.remove(sourceId)
        Unit
    }

    /**
     * Answers the peer's `AcquireSyncLease`.
     *
     * Granted when nobody holds the source, when the peer is re-asking for a lease it already
     * holds, or when both devices are asking at the same moment and the peer wins the tie. The tie
     * is broken by device id - the lower one wins - so both sides reach the same verdict without
     * another round trip, and neither backs off into a livelock.
     */
    suspend fun grantToPeer(
        sourceId: String,
        peerDeviceId: String,
        leaseId: String,
        localDeviceId: String,
    ): Boolean = lock.withLock {
        val granted = when (val current = holderOf(sourceId)) {
            null -> true

            // Our own request is still in flight: the peer with the lower id runs, the other waits
            // for the next round.
            is Lease.Acquiring -> peerDeviceId < localDeviceId

            is Lease.Local -> false

            // Idempotent for the holder: a re-send after a reconnect must not be read as a second
            // device asking.
            is Lease.Remote -> current.peerDeviceId == peerDeviceId
        }

        if (granted) {
            val held = leases[sourceId] as? Lease.Remote

            leases[sourceId] = Lease.Remote(
                peerDeviceId = peerDeviceId,
                leaseId = leaseId,
                expiresAt = timeProvider.now() + LeaseTtl,
            )

            if (held?.peerDeviceId != peerDeviceId) {
                progress.remotePassStarted(sourceId, peerDeviceId)
            }
        }

        granted
    }

    /**
     * Drops a lease the peer gave back, [failure] being whatever it said about how its pass went.
     * Ignores a lease that is no longer theirs. Returns whether one was released.
     */
    suspend fun releaseFromPeer(
        sourceId: String,
        peerDeviceId: String,
        leaseId: String,
        failure: SyncFailureReason? = null,
    ): Boolean =
        lock.withLock {
            val current = leases[sourceId]

            if (current is Lease.Remote &&
                current.peerDeviceId == peerDeviceId &&
                current.leaseId == leaseId
            ) {
                leases.remove(sourceId)
                progress.remotePassFinished(
                    sourceId = sourceId,
                    stage = if (failure == null) {
                        SourcePass.Remote.Stage.Finished
                    } else {
                        SourcePass.Remote.Stage.Failed
                    },
                    failure = failure,
                )
                true
            } else {
                false
            }
        }

    /** Drops everything [peerDeviceId] holds. Called when its session ends, however it ended. */
    suspend fun releaseAllFrom(peerDeviceId: String) = lock.withLock {
        val dropped = leases.entries
            .filter { (_, lease) -> lease is Lease.Remote && lease.peerDeviceId == peerDeviceId }
            .map { it.key }

        dropped.forEach { sourceId ->
            leases.remove(sourceId)
            progress.remotePassFinished(sourceId, SourcePass.Remote.Stage.Abandoned)
        }
    }

    /** The holder of [sourceId], treating an expired peer lease as no holder at all. */
    private fun holderOf(sourceId: String): Lease? {
        val lease = leases[sourceId] ?: return null

        // Backstop for a peer that died without its session reporting it.
        if (lease is Lease.Remote && timeProvider.now() >= lease.expiresAt) {
            leases.remove(sourceId)
            progress.remotePassFinished(sourceId, SourcePass.Remote.Stage.Abandoned)
            return null
        }

        return lease
    }

    private sealed interface Lease {
        /** A local pass has claimed the source and is waiting on the peer's answer. */
        data class Acquiring(val leaseId: String) : Lease

        /** A local pass is running. */
        data class Local(val leaseId: String) : Lease

        data class Remote(
            val peerDeviceId: String,
            val leaseId: String,
            val expiresAt: Instant,
        ) : Lease
    }

    companion object {
        /**
         * How long a peer may hold a source without saying anything.
         *
         * TODO: renew it from the running pass - a source big enough to outlast this loses its
         *  lease mid-transfer.
         */
        private val LeaseTtl = 30.minutes
    }
}
