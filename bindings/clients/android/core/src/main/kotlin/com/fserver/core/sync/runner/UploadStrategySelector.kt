package com.fserver.core.sync.runner

import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import com.fserver.core.sync.model.drivesSync
import com.fserver.core.util.TimeProvider
import com.fserver.files.upload.FilesSnapshot
import com.fserver.files.upload.UploadDecisions
import com.fserver.files.upload.UploadStrategy
import com.fserver.files.upload.impl.HostedUploadStrategy
import com.fserver.files.upload.impl.MirrorUploadStrategy
import com.fserver.files.upload.impl.OneWayUploadStrategy
import kotlin.time.Duration.Companion.days

internal class UploadStrategySelector(
    private val timeProvider: TimeProvider,
) {
    private val strategies: List<UploadStrategy> = listOf(
        MirrorUploadStrategy(),
        OneWayUploadStrategy(),
        HostedUploadStrategy(),
    )

    /** Throws for a [source] this device does not drive: its plan is the peer's to make. */
    suspend fun plan(source: SourceEntry, snapshot: FilesSnapshot): UploadDecisions {
        require(source.drivesSync) {
            "Cannot run ${source.syncMode.type} on a ${source.role.name.lowercase()} device"
        }

        val params = strategyParams(source.syncMode)
        val strategy = strategies.firstOrNull { it.accepts(params) }
            ?: throw IllegalArgumentException("No strategy found for sync mode ${source.syncMode}")

        return strategy.plan(params, snapshot)
    }

    private fun strategyParams(syncMode: SyncMode): UploadStrategy.Params = when (syncMode) {
        is SyncMode.Mirror -> MirrorUploadStrategy.Params

        is SyncMode.AutoUpload -> OneWayUploadStrategy.Params(
            propagateDeletions = true,
            skipModifiedBefore = syncMode.ignoreFilesBefore,
        )

        is SyncMode.Offload -> OneWayUploadStrategy.Params(
            propagateDeletions = false,
            evictWhen = when (val policy = syncMode.policy) {
                is SyncMode.Offload.EvictPolicy.OlderThanDays ->
                    OneWayUploadStrategy.EvictCriterion.ModifiedBefore(timeProvider.now() - policy.days.days)

                is SyncMode.Offload.EvictPolicy.LargerThanBytes ->
                    OneWayUploadStrategy.EvictCriterion.LargerThan(policy.bytes)
            },
        )

        // TODO: writable from Host's access rights, once it has them.
        SyncMode.Host -> HostedUploadStrategy.Params(writable = true)
    }
}
