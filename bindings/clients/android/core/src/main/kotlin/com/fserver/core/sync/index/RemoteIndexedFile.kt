package com.fserver.core.sync.index

import com.fserver.common.model.ContentHash
import com.fserver.common.model.FileSize
import kotlin.time.Instant

/**
 * One file as the peer of a source last reported holding it.
 *
 * The counterpart of [IndexedFile], minus everything that only makes sense locally: there is no row
 * id and no locator, because nothing here addresses bytes on this device. [State] and [Revision]
 * are shared with [IndexedFile] on purpose - they mean the same on both sides, and a third copy is
 * a third place for the eviction/deletion split to drift.
 */
data class RemoteIndexedFile(
    /** Cross-device identity: this is the id the local index uses for the same file. */
    val fileId: String,
    /** Source-relative and canonical file location, as the peer spells it. */
    val path: String,
    /** What the peer holds - a claim about the other side, never about bytes here. */
    val state: IndexedFile.State,
    val size: FileSize,
    val modifiedAt: Instant,
    val hash: ContentHash? = null,
    val revision: IndexedFile.Revision? = null,
    /** When this device last heard the peer say so. */
    val seenAt: Instant,
)
