package com.fserver.core.store.journal

import com.fserver.core.journal.JournalEntry
import com.fserver.core.journal.JournalEvent
import com.fserver.core.store.FServerStorageApi
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * The activity journal. An issue is open while its `solvedAt` is unset; at most one is open per
 * [JournalEvent.Issue.key].
 */
@SubclassOptInRequired(FServerStorageApi::class)
interface JournalStore {
    /** Newest first. */
    val entries: Flow<List<JournalEntry>>

    suspend fun append(event: JournalEvent, at: Instant)

    /**
     * Folds [issue] into the open entry under its key - payload replaced, seen once more - or opens
     * a new entry when there is none.
     */
    suspend fun raise(issue: JournalEvent.Issue, at: Instant)

    /** Returns false when no issue is open under [key]. */
    suspend fun solve(key: String, at: Instant): Boolean

    /** Returns false when [id] is not an open issue. */
    suspend fun solve(id: Long, at: Instant): Boolean

    suspend fun solveForSource(sourceId: String, at: Instant)

    suspend fun solveForDevice(deviceId: String, at: Instant)

    suspend fun openIssues(sourceId: String): List<JournalEntry>

    /** Keeps the newest [keep] entries, and every open issue whatever its age. */
    suspend fun trim(keep: Int)

    /** Drops everything but open issues. */
    suspend fun clear()
}
