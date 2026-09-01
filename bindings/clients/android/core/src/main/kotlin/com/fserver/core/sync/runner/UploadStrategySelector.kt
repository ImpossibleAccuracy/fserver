package com.fserver.core.sync.runner

import com.fserver.core.sync.SyncMode
import com.fserver.files.upload.FilesSnapshot
import com.fserver.files.upload.UploadDecisions
import com.fserver.files.upload.UploadStrategy
import com.fserver.files.upload.impl.MirrorUploadStrategy

internal class UploadStrategySelector {
    private val strategies: List<UploadStrategy> = listOf(
        MirrorUploadStrategy()
    )

    suspend fun plan(syncMode: SyncMode, snapshot: FilesSnapshot): UploadDecisions {
        val params = findStrategyParams(syncMode)
        val strategy = strategies.firstOrNull { it.accepts(params) }
            ?: throw IllegalArgumentException("No strategy found for sync mode $syncMode")

        return strategy.plan(params, snapshot)
    }

    private fun findStrategyParams(syncMode: SyncMode): UploadStrategy.Params =
        when (syncMode) {
            is SyncMode.AutoUpload -> MirrorUploadStrategy.Params()
            is SyncMode.Offload -> MirrorUploadStrategy.Params()
            SyncMode.Mirror -> MirrorUploadStrategy.Params()
        }
}
