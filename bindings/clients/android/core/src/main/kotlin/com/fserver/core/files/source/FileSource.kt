package com.fserver.core.files.source

import com.fserver.core.files.model.FileSize
import com.fserver.core.files.scan.ScanSource
import kotlin.time.Instant

/**
 * One registered directory plus the [AccessModel] it runs under - the pair the engine works from.
 *
 * Constructor is public because a storage backend has to rebuild one from its own columns. Hosts
 * still register through `FilesController.addSource`, which is what assigns [id].
 */
data class FileSource(
    val id: String,
    /** What to walk. */
    val source: ScanSource,
    val accessModel: AccessModel,
    /** Display name, supplied by whoever registered the source. */
    val label: String,
    val createdAt: Instant,
    /** What the last completed scan found. Zeroed until one has run. */
    val fileCount: Int = 0,
    val totalSize: FileSize = FileSize(0),
    /** null until the periodic worker has processed this source once. */
    val lastSyncedAt: Instant? = null,
)
