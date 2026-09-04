package com.fserver.core.sync.progress

import kotlinx.coroutines.flow.Flow

/** What the engine is doing right now. */
interface SyncProgressRepository {
    /** The newest pass per source the engine has run since it started, oldest first. */
    val passes: Flow<List<SourcePass>>

    /** Every transfer the engine knows about, oldest first. */
    val transfers: Flow<List<FileTransfer>>

    fun pass(sourceId: String): Flow<SourcePass?>

    /** Drops what has finished. Anything still moving is left alone. */
    fun clearFinished()
}
