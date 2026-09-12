package com.fserver.core.network.device.model

import com.fserver.core.network.info.model.NetworkInfo
import kotlin.time.Instant

/**
 * What is known about a device rather than about one of its keys: what it claimed to be, and when
 * and where it was last reached.
 *
 * Its own record because `trustedDevice` is keyed by public key and a device may present several -
 * writing these per key duplicates them and lets a device's own rows disagree.
 *
 * Every field is nullable: the facts arrive from different places at different times, and a record
 * pinned by an older build has none of them.
 */
data class DeviceMetadata(
    /** Claimed by the peer, and not proven by the handshake. null when it said nothing this build knows. */
    val kind: DeviceKind?,
    /** Message dictionary the peer's last handshake claimed to speak. */
    val dictionaryId: String?,
    val dictionaryVersion: Int?,
    /** When a handshake with this device last completed. */
    val lastSeen: Instant?,
    /** Network the last connection ran over, as [NetworkInfo.id] reports it. */
    val lastNetworkId: String?,
)
