package com.fserver.core.sync.conflict

import com.fserver.core.sync.version.HlcTimestamp
import kotlin.time.Instant

/**
 * What the user chose for one held conflict, waiting for the next pass to carry it out.
 *
 * Bound to the versions the user compared: if either side has changed by then, the decision is
 * about something the user never saw, so the pass drops it and the conflict is shown again.
 */
data class ConflictDecision(
    val sourceId: String,
    val fileId: String,
    val choice: Choice,
    /** This device's version the user saw, or null when it had none. */
    val local: SeenVersion?,
    /** The peer's version the user saw, or null when it had none. */
    val remote: SeenVersion?,
    val decidedAt: Instant,
) {
    enum class Choice {
        KeepLocal,
        KeepRemote,

        /** Local bytes copied next to the file as a new one, then [KeepRemote]. */
        KeepBoth,
    }

    /** Names one version: every edit issues a fresh (hlc, author) pair, a merge never does. */
    data class SeenVersion(
        val hlc: HlcTimestamp,
        val originDevice: String,
    )
}
