package com.fserver.core.storage

import com.fserver.core.oneshot.model.OneShotTransfer
import kotlinx.coroutines.flow.Flow

/**
 * OneShotTransfer history, as a screen needs it. Starting, answering and cancelling go through the engine;
 * this only lists records and throws finished ones away. Received files stay where they were written.
 */
interface OneShotTransfersRepository {
    /** Newest first. */
    val transfers: Flow<List<OneShotTransfer>>

    fun observe(id: String): Flow<OneShotTransfer?>

    /** Drops a finished transfer's record. Returns false for an unfinished or unknown one. */
    suspend fun delete(id: String): Boolean

    /** Drops every finished transfer's record. */
    suspend fun clearFinished()
}
