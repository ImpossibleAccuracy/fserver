package com.fserver.core.network.dictionary.dto

import kotlinx.serialization.Serializable

/** One device's half of a source, as it reports it. See [com.fserver.core.sync.metadata.PeerSourceMetadata]. */
@Serializable
internal data class SourceMetadataDto(
    val storageKind: StorageKind,
    val storagePath: String? = null,
    val files: Int,
    val bytes: Long,
    val usedPercent: Float? = null,
) {
    @Serializable
    enum class StorageKind { Folder, AppStorage, Media }
}
