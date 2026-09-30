package com.fserver.core.network.dictionary.dto

import kotlinx.serialization.Serializable

/** One file of a one-shot offer, as the sender describes it. */
@Serializable
internal data class OneShotFileDto(
    val index: Int,
    val name: String,
    val size: Long,
)
