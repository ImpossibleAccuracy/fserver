package com.fserver.core.sync

import kotlin.time.Instant

/**
 * What the engine is allowed to do with a source's files.
 */
sealed interface SyncMode {
    data object Mirror : SyncMode

    data class AutoUpload(
        val ignoreFilesBefore: Instant?,
    ) : SyncMode

    data class Offload(
        val policy: EvictPolicy,
        val keepPinned: Boolean,
    ) : SyncMode {
        sealed interface EvictPolicy {
            data class OlderThanDays(val days: Int) : EvictPolicy

            data class LargerThanBytes(val bytes: Long) : EvictPolicy
        }
    }
}
