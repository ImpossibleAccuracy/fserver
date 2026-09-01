package com.fserver.core.sync.index

import kotlinx.serialization.Serializable

/**
 * Addresses one row of the local index. [fileId] is cross-device identity, so it names a row only
 * once paired with the source it was indexed under.
 */
@Serializable
data class IndexedFileKey(
    val fileId: String,
    val sourceId: String,
)
