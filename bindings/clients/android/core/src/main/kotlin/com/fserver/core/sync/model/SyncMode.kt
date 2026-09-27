package com.fserver.core.sync.model

import kotlin.time.Instant

/**
 * What the engine is allowed to do with a source's files.
 */
sealed interface SyncMode {
    /** Which mode this is, without its settings. */
    val type: Type

    enum class Type { Mirror, AutoUpload, Offload }

    data class Mirror(
        val conflictResolution: ConflictResolution,
    ) : SyncMode {
        override val type: Type get() = Type.Mirror

        enum class ConflictResolution {
            /** Hold the conflict: each side keeps its own version until the user picks one. */
            Ask,

            /** Keep the version with the later HLC; the other one's content is lost. */
            LastWriteWins,
        }
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
