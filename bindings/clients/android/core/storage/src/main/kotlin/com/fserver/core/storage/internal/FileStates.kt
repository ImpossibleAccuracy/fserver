package com.fserver.core.storage.internal

import com.fserver.core.sync.index.LocalIndexedFile
import kotlin.time.Instant

/**
 * How an [LocalIndexedFile.State] is stored, for both indexes - what this device holds and what a peer
 * reported holding. Shared so the two cannot spell the same state differently and stop comparing.
 *
 * Three columns rather than one packed value: a discriminator, the flag only `Present` carries, and
 * the timestamp only the other two do.
 */
internal object FileStates {
    private const val Present = "Present"
    private const val Evicted = "Evicted"
    private const val Deleted = "Deleted"

    fun nameOf(state: LocalIndexedFile.State): String = when (state) {
        is LocalIndexedFile.State.Present -> Present
        is LocalIndexedFile.State.Evicted -> Evicted
        is LocalIndexedFile.State.Deleted -> Deleted
    }

    fun pinnedOf(state: LocalIndexedFile.State): Long =
        if ((state as? LocalIndexedFile.State.Present)?.pinned == true) 1 else 0

    /** The one timestamp a state carries, or null for `Present`, which carries none. */
    fun changedAtOf(state: LocalIndexedFile.State): Long? = when (state) {
        is LocalIndexedFile.State.Present -> null
        is LocalIndexedFile.State.Evicted -> state.evictedAt.toEpochMilliseconds()
        is LocalIndexedFile.State.Deleted -> state.deletedAt.toEpochMilliseconds()
    }

    /**
     * A row missing the timestamp its state needs reads as `Present` rather than inventing one: a
     * made-up `deletedAt` is a deletion this device would go on to propagate.
     *
     * A discriminator written by a newer build reads as `Present` for the same reason - the safe
     * wrong answer is "the file is still here".
     */
    fun read(state: String, pinned: Long, changedAtEpochMs: Long?): LocalIndexedFile.State {
        val changedAt = changedAtEpochMs?.let(Instant::fromEpochMilliseconds)

        return when (state) {
            Evicted -> changedAt?.let { LocalIndexedFile.State.Evicted(evictedAt = it) }
                ?: LocalIndexedFile.State.Present()

            Deleted -> changedAt?.let { LocalIndexedFile.State.Deleted(deletedAt = it) }
                ?: LocalIndexedFile.State.Present()

            else -> LocalIndexedFile.State.Present(pinned = pinned == 1L)
        }
    }
}
