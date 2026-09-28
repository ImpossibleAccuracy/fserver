package com.fserver.core.sync.progress.impl

import com.fserver.core.sync.progress.SourcePass
import com.fserver.core.sync.progress.SyncFailureReason
import com.fserver.core.sync.progress.toSyncFailure
import com.fserver.core.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** The newest pass per source, from whichever side of it this device is on. */
internal class PassTracker(
    private val timeProvider: TimeProvider,
) {
    private val state = MutableStateFlow<Map<String, SourcePass>>(emptyMap())

    val passes: Flow<List<SourcePass>> =
        state.map { passes -> passes.values.sortedBy { it.startedAt } }

    fun pass(sourceId: String): Flow<SourcePass?> =
        state.map { it[sourceId] }.distinctUntilChanged()

    fun clearFinished() {
        state.update { passes -> passes.filterValues { !it.isFinished } }
    }

    fun localStarted(sourceId: String) {
        val now = timeProvider.now()

        state.update {
            it + (sourceId to SourcePass.Local(
                sourceId = sourceId,
                startedAt = now,
                updatedAt = now,
                stage = SourcePass.Local.Stage.Scanning,
            ))
        }
    }

    /**
     * Ignores a source the peer holds: a local report against a remote pass would mean the lease
     * let both sides run at once, and overwriting it would hide that rather than show it.
     */
    fun updateLocal(sourceId: String, transform: (SourcePass.Local) -> SourcePass.Local) {
        state.update { passes ->
            val existing = passes[sourceId] as? SourcePass.Local ?: return@update passes
            passes + (sourceId to transform(existing).copy(updatedAt = timeProvider.now()))
        }
    }

    /** A pass the peer is still driving is left alone: its lease is why this one got nowhere. */
    fun localAborted(sourceId: String, failure: Throwable) {
        val now = timeProvider.now()

        state.update { passes ->
            val existing = passes[sourceId]
            if (existing is SourcePass.Remote && !existing.isFinished) return@update passes

            passes + (sourceId to SourcePass.Local(
                sourceId = sourceId,
                startedAt = now,
                updatedAt = now,
                stage = SourcePass.Local.Stage.Failed,
                failure = failure.toSyncFailure(),
            ))
        }
    }

    fun remoteStarted(sourceId: String, peerDeviceId: String) {
        val now = timeProvider.now()

        state.update {
            it + (sourceId to SourcePass.Remote(
                sourceId = sourceId,
                startedAt = now,
                updatedAt = now,
                peerDeviceId = peerDeviceId,
                stage = SourcePass.Remote.Stage.Serving,
            ))
        }
    }

    fun remoteFinished(sourceId: String, stage: SourcePass.Remote.Stage, failure: SyncFailureReason?) {
        state.update { passes ->
            val existing = passes[sourceId] as? SourcePass.Remote ?: return@update passes

            passes + (sourceId to existing.copy(
                stage = stage,
                failure = failure,
                updatedAt = timeProvider.now(),
            ))
        }
    }
}
