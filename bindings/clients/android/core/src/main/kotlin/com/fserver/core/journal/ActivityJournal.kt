package com.fserver.core.journal

import com.fserver.common.utils.runBackgroundJob
import com.fserver.core.store.FServerStorage
import com.fserver.core.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * What the engine did and ran into, newest first. The engine writes it and solves issues it sees
 * gone; the user may only dismiss an issue or clear what is settled.
 */
class ActivityJournal internal constructor(
    private val storage: FServerStorage,
    private val timeProvider: TimeProvider,
) {
    val entries: Flow<List<JournalEntry>> get() = storage.journal.entries

    /** Issues neither the engine nor the user has solved yet, newest first. */
    val openIssues: Flow<List<JournalEntry>> = storage.journal.entries
        .map { entries ->
            entries.filter { it.issue?.solved == false }
        }

    /** Dismisses issue [id]. If the problem comes back, it opens a new entry. */
    suspend fun solve(id: Long): Result<Boolean> = runBackgroundJob {
        storage.journal.solve(id, timeProvider.now())
    }

    /** Drops every entry except open issues. */
    suspend fun clear(): Result<Unit> = runBackgroundJob {
        storage.journal.clear()
    }
}
