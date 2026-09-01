package com.fserver.core.sync

import com.fserver.common.model.FileSize
import com.fserver.core.files.SourceLocation
import kotlin.time.Instant

/**
 * One registered directory plus the [SyncMode] it runs under - the pair the engine works from.
 *
 * Constructor is public because a storage backend has to rebuild one from its own columns. Hosts
 * still register through [SourcesController.addSource], which is what assigns [id].
 */
data class SourceEntry(
    val id: String,
    /** The device that registered this source. */
    val deviceId: String,
    /** What to walk. */
    val location: SourceLocation,
    val syncMode: SyncMode,
    /** Display name, supplied by whoever registered the source. */
    val label: String,
    val createdAt: Instant,
    /** What the last completed scan found. Zeroed until one has run. */
    val fileCount: Int = 0,
    val totalSize: FileSize = FileSize(0),
    /** null until a sync pass has processed this source once. */
    val lastSyncedAt: Instant? = null,
)
