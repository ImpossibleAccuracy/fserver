package com.fserver.core.sync.model

import kotlin.time.Instant

/**
 * What the engine is allowed to do with a source's files.
 */
sealed interface SyncMode {
    /** Which mode this is, without its settings. */
    val type: Type

    enum class Type { Mirror, AutoUpload, Offload }

    data object Mirror : SyncMode {
        override val type: Type get() = Type.Mirror
    }

    data class AutoUpload(
        val ignoreFilesBefore: Instant?,
    ) : SyncMode {
        override val type: Type get() = Type.AutoUpload
    }

    data class Offload(
        val policy: EvictPolicy,
        val keepPinned: Boolean,
    ) : SyncMode {
        override val type: Type get() = Type.Offload

        sealed interface EvictPolicy {
            data class OlderThanDays(val days: Int) : EvictPolicy

            data class LargerThanBytes(val bytes: Long) : EvictPolicy
        }
    }
}
