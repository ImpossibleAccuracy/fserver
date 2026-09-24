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
) {
    val isRemote: Boolean
        get() = locator == null
}
