package com.fserver.core.sync.runner

import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import com.fserver.files.upload.FilesSnapshot
import com.fserver.files.upload.UploadDecisions
import com.fserver.files.upload.UploadStrategy
import com.fserver.files.upload.impl.MirrorUploadStrategy

internal class UploadStrategySelector {
    private val strategies: List<UploadStrategy> = listOf(
        MirrorUploadStrategy()
    )

    suspend fun plan(
        syncMode: SyncMode,
        role: SourceEntry.Role,
        snapshot: FilesSnapshot,
    ): UploadDecisions {
        val params = findStrategyParams(syncMode, role)
        val strategy = strategies.firstOrNull { it.accepts(params) }
            ?: throw IllegalArgumentException("No strategy found for sync mode $syncMode")

        return strategy.plan(params, snapshot)
    }

    /**
     * [role] is what gives a one-way mode its direction: the same [SyncMode] means "send" on the
     * initiator and "receive" on the follower, and only the role says which end this device is.
     */
    private fun findStrategyParams(
        syncMode: SyncMode,
        role: SourceEntry.Role,
    ): UploadStrategy.Params = when (syncMode) {
        is SyncMode.Mirror -> MirrorUploadStrategy.Params()

        // Files travel initiator -> follower and never back: the initiator does not pull down what
        // it already sent, so only the follower restores what it is missing.
        is SyncMode.AutoUpload,
        is SyncMode.Offload -> MirrorUploadStrategy.Params(
            restoreMissingLocalFiles = role == SourceEntry.Role.Follower,
        )
    }
}
