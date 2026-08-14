package com.fserver.net.discovery

import com.fserver.net.peer.PeerDescriptor
import com.fserver.net.security.auth.AuthMethodId

/**
 * What a device broadcast about itself before anyone connected. Every field is the peer's own
 * claim: a scan proves only that something answered, never what it is.
 *
 * There is no fingerprint here and no dictionary. A stable key fingerprint on the air is what lets
 * a passive listener follow a device from network to network, and the dictionary says nothing a
 * connection would not say better - both belong behind the handshake.
 *
 * The confirmed counterpart is [PeerDescriptor], which only a completed handshake produces.
 *
 * @param methods What the peer says it will accept. Hint for user, no more -
 * transport that fixes its own method ignores this and reads that method off its own declaration.
 */
data class AdvertisedPeer(
    val deviceId: String,
    val displayName: String,
    val kind: PeerDescriptor.Kind? = null,
    val protocolVersions: IntRange? = null,
    val methods: List<AuthMethodId> = emptyList(),
)
