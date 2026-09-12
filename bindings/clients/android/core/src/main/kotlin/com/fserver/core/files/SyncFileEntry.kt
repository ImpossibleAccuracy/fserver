package com.fserver.core.files

import com.fserver.common.model.FileSize
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.index.LocalIndexedFile.Revision
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
    val revision: Revision? = null,
) {
    val isRemote: Boolean
        get() = locator == null
}
