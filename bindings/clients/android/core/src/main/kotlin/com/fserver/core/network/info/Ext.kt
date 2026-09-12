package com.fserver.core.network.info

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * The id of the link this device is on right now, or null when there is none - or when the read
 * took too long to be worth holding a connection open for.
 *
 * Timed out rather than awaited: every caller records it as a side note on an operation that has
 * already succeeded, and a registration that has yet to settle must not hold that operation up.
 */
internal suspend fun NetworkInfoRepository.currentNetworkId(): String? =
    withTimeoutOrNull(READ_TIMEOUT) { networkInfo.first()?.id }

/** Long enough for the platform callback to settle, short enough not to stall a handshake. */
private val READ_TIMEOUT = 1_000.milliseconds
