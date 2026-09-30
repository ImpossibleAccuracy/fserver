package com.fserver.core.network.dictionary.dto

import com.fserver.core.sync.index.IndexedFileKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Which file an upload moves, and so where the receiver puts it. */
@Serializable
internal sealed interface UploadKey {
    /** A file of a synced source. */
    @Serializable
    @SerialName("source")
    data class Source(val sourceId: String, val fileId: String) : UploadKey {
        fun toIndexed() = IndexedFileKey(fileId = fileId, sourceId = sourceId)
    }

    /** File [index] of a one-shot transfer. */
    @Serializable
    @SerialName("oneShot")
    data class OneShot(val transferId: String, val index: Int) : UploadKey
}

internal fun IndexedFileKey.toUploadKey() = UploadKey.Source(sourceId = sourceId, fileId = fileId)
