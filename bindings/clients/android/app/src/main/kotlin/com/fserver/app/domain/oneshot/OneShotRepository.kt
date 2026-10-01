package com.fserver.app.domain.oneshot

import com.fserver.core.files.SourceLocation
import kotlinx.coroutines.flow.Flow

/**
 * What `:core` leaves to the host about one-shot transfers: the preferences, auto-accepting, and
 * telling the user about transfers outside the app's screens.
 */
interface OneShotRepository {
    /** Whether incoming offers are accepted without asking. */
    val autoAccept: Flow<Boolean>

    /** Where accepted transfers are written: the user's pick, or a default that needs no grant. */
    val destination: Flow<SourceLocation.Hostable>

    suspend fun setAutoAccept(enabled: Boolean)

    suspend fun setDestination(location: SourceLocation.Hostable)

    /**
     * Accepts every incoming offer into [destination] while [autoAccept] is on, once per transfer:
     * a failed one waits for the user. Runs until cancelled.
     */
    suspend fun runBackgroundWork()

    /**
     * Keeps the process up while bytes move, and turns offers into notifications while
     * [appVisible] is false - in front of the user, the in-app sheet asks instead. Runs until
     * cancelled.
     */
    suspend fun linkVisibility(appVisible: Flow<Boolean>)
}
