package com.fserver.core.files

import com.fserver.common.model.FileSize
import com.fserver.core.sync.index.LocalIndexedFile
import kotlin.time.Instant

data class SyncFileEntry(
    val fileId: String,
    val sourceId: String,
    val path: String,
    val locator: String?,
    val size: FileSize,
    val localState: LocalIndexedFile.State?,
    val remoteState: LocalIndexedFile.State?,
    val modifiedAt: Instant,
    /** Evicted here, and the peer no longer holds that version - deleted or changed there. */
    val lostOnPeer: Boolean = false,
    /** Why the local bytes may not be evicted, or null when they may. Ignores the source's mode. */
    val evictRefusal: EvictRefusal? = EvictRefusal.NotHere,
) {
    val isRemote: Boolean
        get() = locator == null
}
