package com.fserver.core.journal

import kotlin.time.Instant

/** A stored [JournalEvent]. */
data class JournalEntry(
    val id: Long,
    val event: JournalEvent,
    /** When it happened; for an issue, when it was first seen. */
    val at: Instant,
    /** Set exactly when [event] is a [JournalEvent.Issue]. */
    val issue: IssueState?,
)

data class IssueState(
    val lastSeenAt: Instant,
    /** How many times the problem was seen while this entry was open. */
    val occurrences: Int,
    val solvedAt: Instant?,
) {
    val solved: Boolean get() = solvedAt != null
}
