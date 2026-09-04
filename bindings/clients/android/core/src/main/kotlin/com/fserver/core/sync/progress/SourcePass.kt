package com.fserver.core.sync.progress

import kotlin.time.Instant

/**
 * A pass over one source, from whichever side of it this device is on.
 *
 * The two sides are not the same shape and are deliberately not flattened into one. The device
 * holding the lease scanned, planned, and knows how much work the pass is; the device serving it
 * is answering one request at a time and cannot know what the peer will ask for next. Reporting a
 * count on the serving side would mean inventing one.
 *
 * Only one of these exists per source at a time - the lease is what guarantees it.
 */
sealed interface SourcePass {
    val sourceId: String
    val startedAt: Instant
    val updatedAt: Instant
    val isFinished: Boolean

    /**
     * This device holds the lease and drives the pass.
     *
     * Counted in actions, not files on disk: a pass only touches what the plan says has to move,
     * so "12 of 40" means forty planned actions, not forty files in the source.
     */
    data class Local(
        override val sourceId: String,
        override val startedAt: Instant,
        override val updatedAt: Instant,
        val stage: Stage,
        val actionsPlanned: Int = 0,
        val actionsDone: Int = 0,
        /** Why the pass gave up, when it did. */
        val failure: String? = null,
    ) : SourcePass {
        override val isFinished: Boolean
            get() = stage == Stage.Finished || stage == Stage.Failed

        /** `null` until the plan exists - there is nothing to count against before that. */
        val progress: Float?
            get() = if (actionsPlanned <= 0) null
            else (actionsDone.toFloat() / actionsPlanned).coerceIn(0f, 1f)

        enum class Stage {
            /** Walking the source and bringing the local index in line with what is on disk. */
            Scanning,

            /** Both indexes in hand, working out what has to move. */
            Planning,

            /** Carrying the plan out. */
            Transferring,

            Finished,

            Failed,
        }
    }

    /**
     * The peer holds the lease; this device only answers what it asks for.
     *
     * No stage breakdown and no counts, because none of it is knowable here - the detail the user
     * can see is the files themselves, which arrive as [FileTransfer].
     */
    data class Remote(
        override val sourceId: String,
        override val startedAt: Instant,
        override val updatedAt: Instant,
        val peerDeviceId: String,
        val stage: Stage,
    ) : SourcePass {
        override val isFinished: Boolean get() = stage != Stage.Serving

        enum class Stage {
            /** The peer holds the lease and is working through the source. */
            Serving,

            /** The peer handed the lease back, which is how a pass ends normally. */
            Finished,

            /** The lease went away without being handed back - the session died, or it timed out. */
            Abandoned,
        }
    }
}
