package com.fserver.core.network.dictionary.dto

import kotlinx.serialization.Serializable

/** One device's half of a source, as it reports it. See [com.fserver.core.sync.metadata.PeerSourceMetadata]. */
@Serializable
internal data class SourceMetadataDto(
    val storagePath: String,
    val files: Int,
    val bytes: Long,
    val usedPercent: Float? = null,
)
