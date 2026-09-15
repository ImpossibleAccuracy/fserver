package com.fserver.core.sync.index

import com.fserver.common.model.ContentHash
import com.fserver.common.model.FileSize
import kotlin.time.Instant

/** One file as the peer of a source last reported holding it. */
data class RemoteIndexedFile(
    val sourceId: String,
    /** Cross-device identity: this is the id the local index uses for the same file. */
    val fileId: String,
    /** Source-relative and canonical file location, as the peer spells it. */
    val path: String,
    /** What the peer holds - a claim about the other side, never about bytes here. */
    val state: LocalIndexedFile.State,
    val size: FileSize,
    val modifiedAt: Instant,
    val hash: ContentHash? = null,
    val revision: LocalIndexedFile.Revision? = null,
    /** When this device last heard the peer say so. */
    val seenAt: Instant,
) {
    val isDeleted: Boolean
        get() = state is LocalIndexedFile.State.Deleted
}
