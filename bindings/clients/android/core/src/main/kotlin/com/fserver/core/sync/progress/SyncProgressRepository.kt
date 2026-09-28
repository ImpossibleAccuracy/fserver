package com.fserver.core.sync.progress

import kotlinx.coroutines.flow.Flow

/** What the engine is doing right now. */
interface SyncProgressRepository {
    /** The newest pass per source the engine has run since it started, oldest first. */
    val passes: Flow<List<SourcePass>>

    /** Every transfer the engine knows about, oldest first. */
    val transfers: Flow<List<FileTransfer>>

    fun pass(sourceId: String): Flow<SourcePass?>

    /** The newest indexing run over [sourceId], whether a pass, a peer or the host started it. */
    fun indexing(sourceId: String): Flow<IndexingProgress?>

    /** Drops what has finished. Anything still moving is left alone. */
    fun clearFinished()
}
