package com.fserver.core.sync.conflict

import com.fserver.common.utils.runBackgroundJob
import com.fserver.core.di.BackgroundScope
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.index.RemoteIndexedFile
import com.fserver.core.sync.index.toFileRecord
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import com.fserver.core.sync.runner.SyncRunner
import com.fserver.core.sync.runner.UploadStrategySelector
import com.fserver.core.util.TimeProvider
import com.fserver.files.upload.FileAction
import com.fserver.files.upload.FilesSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Conflicts held for the user, in sources that [ask][SyncMode.Mirror.ConflictResolution.Ask].
 *
 * Nothing records a conflict: it is re-derived by planning over this device's index and the peer's
 * last reported one, so both devices see it without telling each other, and it disappears on its
 * own once a version newer than both lands.
 */
class ConflictsController internal constructor(
    private val storage: FServerStorage,
    private val strategySelector: UploadStrategySelector,
    private val syncRunner: SyncRunner,
    private val timeProvider: TimeProvider,
    scope: BackgroundScope,
) {
    /**
     * Every held conflict without a decision yet. A decided one drops out before its pass runs,
     * and comes back at once if either side changes past what the user decided on.
     */
    val pending: StateFlow<List<FileConflict>> = combine(
        storage.index.all,
        storage.remoteIndex.all,
        storage.conflictDecisions.all,
    ) { local, remote, decisions ->
        val decided = decisions.associateBy { it.sourceId to it.fileId }
        val localDevice = storage.identity.localDevice().deviceId

        val localBySource = local.groupBy { it.sourceId }
        val remoteBySource = remote.groupBy { it.sourceId }

        storage.sources.all()
            .filter { it.asksOnConflict() }
            .flatMap { source ->
                // TODO: non-optimized solution, rewrite
                held(
                    source = source,
                    localDevice = localDevice,
                    local = localBySource[source.id].orEmpty(),
                    remote = remoteBySource[source.id].orEmpty(),
                )
            }
            .filterNot { decided[it.sourceId to it.fileId]?.covers(it) == true }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Records the user's [choice] and starts a pass over the source to carry it out. The pass drops
     * it if either side changed since [conflict] was read.
     */
    suspend fun resolve(conflict: FileConflict, choice: ConflictDecision.Choice): Result<Unit> = runBackgroundJob {
        require(choice in conflict.choices) { "$choice is not available for ${conflict.path}" }

        storage.conflictDecisions.put(
            ConflictDecision(
                sourceId = conflict.sourceId,
                fileId = conflict.fileId,
                choice = choice,
                local = conflict.local.version?.seen(),
                remote = conflict.remote.version?.seen(),
                decidedAt = timeProvider.now(),
            )
        )

        syncRunner.runSourceAsync(conflict.sourceId)
    }

    /** Same planning a pass does, over the cached indexes; only its conflicts are kept. */
    private suspend fun held(
        source: SourceEntry,
        localDevice: String,
        local: List<LocalIndexedFile>,
        remote: List<RemoteIndexedFile>,
    ): List<FileConflict> {
        if (remote.isEmpty()) return emptyList()

        val snapshot = FilesSnapshot(
            local = local.map { it.toFileRecord() },
            remote = remote.map { it.toFileRecord() },
        )

        val localById = local.associateBy { it.fileId }
        val remoteById = remote.associateBy { it.fileId }

        return strategySelector.plan(source, snapshot)
            .filterIsAction<FileAction.Conflict>()
            .mapNotNull { action ->
                val here = localById[action.id.value] ?: return@mapNotNull null
                val there = remoteById[action.id.value] ?: return@mapNotNull null

                FileConflict(
                    sourceId = source.id,
                    fileId = here.fileId,
                    path = here.path,
                    local = FileConflict.Side(localDevice, here.state, here.size, here.modifiedAt, here.version),
                    remote = FileConflict.Side(source.deviceId, there.state, there.size, there.modifiedAt, there.version),
                )
            }
    }

    private fun ConflictDecision.covers(conflict: FileConflict): Boolean =
        local == conflict.local.version?.seen() && remote == conflict.remote.version?.seen()

    private fun SourceEntry.asksOnConflict(): Boolean =
        status == SourceEntry.Status.Active &&
                (syncMode as? SyncMode.Mirror)?.conflictResolution == SyncMode.Mirror.ConflictResolution.Ask
}
